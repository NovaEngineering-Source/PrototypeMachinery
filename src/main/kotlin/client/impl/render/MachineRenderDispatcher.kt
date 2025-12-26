package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.assets.ExternalDiskTextureBinder
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomBridge
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.util.ReusableVboUploader
import net.minecraft.client.Minecraft
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
     * Flush all pending renders in correct pass order.
     *
     * Called by Mixin after TileEntityRendererDispatcher.drawBatch() completes.
     * This ensures all machine DEFAULT passes render first, then all TRANSPARENT.
     */
    fun flush() {
        if (pendingRenders.isEmpty()) return

        val dataList = pendingRenders.toList()
        pendingRenders.clear()

        val deferBloomToPost = GregTechBloomBridge.isEnabled

        GlStateManager.pushMatrix()
        GlStateManager.translate(
            -TileEntityRendererDispatcher.staticPlayerX,
            -TileEntityRendererDispatcher.staticPlayerY,
            -TileEntityRendererDispatcher.staticPlayerZ
        )

        try {
            withLightmapEnabled {
                // Phase 1: Render all DEFAULT (opaque) passes
                renderPass(dataList, RenderPass.DEFAULT)

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
                    renderPassWithoutState(dataList, RenderPass.BLOOM)
                    RenderTypeState.post(RenderPass.BLOOM)

                    RenderTypeState.pre(RenderPass.BLOOM_TRANSPARENT)
                    renderPassWithoutState(dataList, RenderPass.BLOOM_TRANSPARENT)
                    RenderTypeState.post(RenderPass.BLOOM_TRANSPARENT)
                }
            }
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
        }
    }

    private fun renderPass(dataList: List<PendingRenderData>, pass: RenderPass) {
        for (data in dataList) {
            data.built.byPass[pass]?.let { buffer ->
                ExternalDiskTextureBinder.bind(data.texture)
                setLightmapCoords(data.combinedLight)
                uploader.draw(buffer)
            }
        }
    }

    private fun renderPassWithoutState(dataList: List<PendingRenderData>, pass: RenderPass) {
        for (data in dataList) {
            data.built.byPass[pass]?.let { buffer ->
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
