package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.assets.ExternalDiskTextureBinder
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomBridge
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.util.ReusableVboUploader
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11

/**
 * Centralized render dispatcher for machine models.
 *
 * Collects render data from all machine TESRs during the TESR batch phase,
 * then renders everything in correct pass order after all TESRs have executed.
 *
 * This architecture enables:
 * 1. Correct transparent rendering order across all machines
 * 2. Future multi-threaded buffer building
 * 3. Proper integration with GT bloom pipeline
 */
internal object MachineRenderDispatcher {

    private val uploader = ReusableVboUploader()

    /**
     * Per-frame collected render data from all machine TESRs.
     * Cleared after each flush.
     */
    private val pendingRenders = mutableListOf<PendingRenderData>()

    /**
     * Data class holding render information for a single machine/sub-structure.
     */
    data class PendingRenderData(
        val texture: ResourceLocation,
        val combinedLight: Int,
        val built: BuiltBuffers,
    )

    /**
     * Called by TESR to submit render data for deferred batch rendering.
     */
    fun submit(data: PendingRenderData) {
        pendingRenders.add(data)
    }

    /**
     * Called by TESR to submit multiple render data entries.
     */
    fun submitAll(dataList: List<PendingRenderData>) {
        pendingRenders.addAll(dataList)
    }

    /**
     * Returns true if there are pending renders to flush.
     */
    fun hasPendingWork(): Boolean = pendingRenders.isNotEmpty()

    /**
     * Clear all pending render data and release any reusable GL resources.
     *
     * Intended for world unload / resource reload.
     */
    fun clearAll() {
        pendingRenders.clear()
        uploader.dispose()
    }

    /**
     * Flush all pending renders in correct pass order.
     *
     * Called by Mixin after TileEntityRendererDispatcher.drawBatch() completes.
     * This ensures all machine DEFAULT passes render first, then all TRANSPARENT.
     */
    fun flush() {
        if (pendingRenders.isEmpty()) {
            return
        }

        val dataList = pendingRenders.toList()
        pendingRenders.clear()

        RenderStats.noteDispatcherPending(dataList.size)

        val deferBloomToPost = GregTechBloomBridge.isEnabled

        GlStateManager.pushMatrix()
        // IMPORTANT:
        // Our VBOs are baked in absolute world coordinates (we use te.pos.x/y/z in the bake task).
        // When drawing here (after TESR batch), the current modelview may NOT have the camera-origin
        // translation applied. RenderManager (used by the GT bloom callback) always applies -camera.
        // If we don't do the same here, DEFAULT/TRANSPARENT will appear offset while BLOOM looks correct.
        GlStateManager.translate(
            -TileEntityRendererDispatcher.staticPlayerX,
            -TileEntityRendererDispatcher.staticPlayerY,
            -TileEntityRendererDispatcher.staticPlayerZ
        )

        try {
            withLightmapEnabled {
                // Phase 1: Render all DEFAULT (opaque) passes
                renderPassBatched(dataList, RenderPass.DEFAULT, applyLightmap = true)

                // Phase 2: Render all TRANSPARENT passes
                RenderTypeState.pre(RenderPass.TRANSPARENT)
                renderPassWithoutState(dataList, RenderPass.TRANSPARENT)
                RenderTypeState.post(RenderPass.TRANSPARENT)

                // Phase 3: Handle BLOOM passes
                if (deferBloomToPost) {
                    // Defer to RenderManager for GT bloom callback
                    for (data in dataList) {
                        data.built.byPass[RenderPass.BLOOM]?.let { buffer ->
                            RenderManager.addBuffer(RenderPass.BLOOM, data.texture, -1, buffer)
                        }
                        data.built.byPass[RenderPass.BLOOM_TRANSPARENT]?.let { buffer ->
                            RenderManager.addBuffer(RenderPass.BLOOM_TRANSPARENT, data.texture, -1, buffer)
                        }
                    }
                } else {
                    // No GT bloom: render directly
                    RenderTypeState.pre(RenderPass.BLOOM)
                    renderPassBatched(dataList, RenderPass.BLOOM, applyLightmap = false)
                    RenderTypeState.post(RenderPass.BLOOM)

                    RenderTypeState.pre(RenderPass.BLOOM_TRANSPARENT)
                    renderPassBatched(dataList, RenderPass.BLOOM_TRANSPARENT, applyLightmap = false)
                    RenderTypeState.post(RenderPass.BLOOM_TRANSPARENT)
                }
            }
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
        }
    }

    /**
     * Batched renderer for passes where draw order does NOT affect correctness.
     *
     * We bucket by texture + combinedLight so we can reduce state changes and glDrawArrays calls
     * (via [ReusableVboUploader.drawMultiple]).
     */
    private fun renderPassBatched(dataList: List<PendingRenderData>, pass: RenderPass, applyLightmap: Boolean) {
        if (dataList.isEmpty()) return

        // Texture -> Light -> Buffers
        val buckets: MutableMap<ResourceLocation, Int2ObjectOpenHashMap<MutableList<BufferBuilder>>> =
            Object2ObjectOpenHashMap()

        for (data in dataList) {
            val buffer = data.built.byPass[pass] ?: continue
            val light = if (applyLightmap) data.combinedLight else -1
            buckets
                .computeIfAbsent(data.texture) { Int2ObjectOpenHashMap() }
                .computeIfAbsent(light) { ObjectArrayList() }
                .add(buffer)
        }

        if (buckets.isEmpty()) return

        buckets.forEach { (texture, lightMap) ->
            RenderStats.addTextureBind()
            ExternalDiskTextureBinder.bind(texture)
            lightMap.forEach { (light, bufferList) ->
                if (applyLightmap) {
                    setLightmapCoords(light)
                }
                RenderStats.noteMergeBucket(bufferList.size)
                uploader.drawMultiple(bufferList)
            }
        }
    }

    private fun renderPassWithoutState(dataList: List<PendingRenderData>, pass: RenderPass) {
        for (data in dataList) {
            data.built.byPass[pass]?.let { buffer ->
                RenderStats.addTextureBind()
                ExternalDiskTextureBinder.bind(data.texture)
                if (pass != RenderPass.BLOOM && pass != RenderPass.BLOOM_TRANSPARENT) {
                    setLightmapCoords(data.combinedLight)
                }
                uploader.draw(buffer)
            }
        }
    }

    private fun setLightmapCoords(combinedLight: Int) {
        if (combinedLight != -1) {
            val lx = combinedLight % 65536
            val ly = combinedLight / 65536
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx.toFloat(), ly.toFloat())
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
        }
    }

    private inline fun <T> withLightmapEnabled(block: () -> T): T {
        val mc = Minecraft.getMinecraft()

        // Snapshot lightmap state
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
        val wasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        val prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

        GL11.glMatrixMode(GL11.GL_TEXTURE)
        GL11.glPushMatrix()
        GL11.glMatrixMode(GL11.GL_MODELVIEW)

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)

        // Enable vanilla lightmap
        mc.entityRenderer.enableLightmap()

        return try {
            block()
        } finally {
            // Restore lightmap state
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)

            GL11.glMatrixMode(GL11.GL_TEXTURE)
            GL11.glPopMatrix()
            GL11.glMatrixMode(GL11.GL_MODELVIEW)

            GlStateManager.bindTexture(prevBinding)

            if (!wasEnabled) {
                GlStateManager.disableTexture2D()
            } else {
                GlStateManager.enableTexture2D()
            }

            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
        }
    }
}
