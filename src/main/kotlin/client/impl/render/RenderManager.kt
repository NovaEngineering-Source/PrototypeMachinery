package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.assets.ExternalDiskTextureBinder
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomBridge
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
import java.util.EnumMap

/**
 * Collects buffers (typically produced off-thread) and draws them in batches.
 *
 * This class is client-only and intentionally independent from machine logic.
 */
internal object RenderManager {

    private val uploader = ReusableVboUploader()

    // RenderPass -> Texture -> Light -> Buffers
    private val buckets: MutableMap<RenderPass, MutableMap<ResourceLocation, Int2ObjectOpenHashMap<MutableList<BufferBuilder>>>> =
        EnumMap(RenderPass::class.java)

    fun addBuffer(pass: RenderPass, texture: ResourceLocation, combinedLight: Int, buffer: BufferBuilder) {
        buckets
            .computeIfAbsent(pass) { Object2ObjectOpenHashMap() }
            .computeIfAbsent(texture) { Int2ObjectOpenHashMap() }
            .computeIfAbsent(combinedLight) { ObjectArrayList() }
            .add(buffer)
    }

    /**
     * Clear all queued buffers and release reusable GL resources.
     *
     * Intended for world unload / resource reload.
     */
    fun clearAll() {
        buckets.clear()
        uploader.dispose()
    }

    /**
     * Draw all queued buffers and clear internal state.
     *
     * Must be called on the render thread.
     */
    fun drawAll() {
        if (buckets.isEmpty()) return

        RenderStats.noteRenderManagerBuckets(buckets.size)

        val deferBloomToPost = GregTechBloomBridge.isEnabled

        // RenderWorldLastEvent can run with lightmap disabled depending on other pipelines.
        // Ensure lightmap is enabled so DEFAULT/TRANSPARENT passes pick up world lighting.
        val entityRenderer = Minecraft.getMinecraft().entityRenderer
        entityRenderer.enableLightmap()

        GlStateManager.pushMatrix()
        // Prevent precision issues for large coordinates by translating to camera origin.
        GlStateManager.translate(
            -TileEntityRendererDispatcher.staticPlayerX,
            -TileEntityRendererDispatcher.staticPlayerY,
            -TileEntityRendererDispatcher.staticPlayerZ
        )

        try {
            // When bloom post-processing is available, keep BLOOM passes queued for the bloom callback.
            val it = buckets.entries.iterator()
            while (it.hasNext()) {
                val (pass, textures) = it.next()

                if (deferBloomToPost && isBloomPass(pass)) {
                    // Keep for GregTechBloomBridge callback.
                    continue
                }

                RenderTypeState.pre(pass)
                textures.forEach { (texture, lightMap) ->
                    RenderStats.addTextureBind()
                    ExternalDiskTextureBinder.bind(texture)
                    lightMap.forEach { (light, bufferList) ->
                        if (light != -1) {
                            val lx = light % 65536
                            val ly = light / 65536
                            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
                            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx.toFloat(), ly.toFloat())
                            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
                        }
                        RenderStats.noteMergeBucket(bufferList.size)
                        uploader.drawMultiple(bufferList)
                    }
                }
                RenderTypeState.post(pass)
                it.remove()
            }
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
            entityRenderer.disableLightmap()
        }
    }

    internal fun hasPendingBloomWork(): Boolean {
        if (buckets.isEmpty()) return false
        return buckets.keys.any(::isBloomPass)
    }

    /**
     * Draw bloom-capable passes.
     *
     * When GregTech bloom post-processing is present, this is invoked from its callback.
     * We intentionally map LUMENIZED passes onto BLOOM state here (no additive blending),
     * because the bloom pipeline itself handles post-processing/accumulation.
     */
    internal fun drawBloomPasses(clearAfterDraw: Boolean) {
        if (!hasPendingBloomWork()) return

        RenderStats.noteRenderManagerBuckets(buckets.size)

        GlStateManager.pushMatrix()
        GlStateManager.translate(
            -TileEntityRendererDispatcher.staticPlayerX,
            -TileEntityRendererDispatcher.staticPlayerY,
            -TileEntityRendererDispatcher.staticPlayerZ
        )

        try {
            val it = buckets.entries.iterator()
            while (it.hasNext()) {
                val (pass, textures) = it.next()
                if (!isBloomPass(pass)) continue

                RenderTypeState.pre(pass)
                textures.forEach { (texture, lightMap) ->
                    RenderStats.addTextureBind()
                    ExternalDiskTextureBinder.bind(texture)
                    lightMap.forEach { (light, bufferList) ->
                        if (light != -1) {
                            val lx = light % 65536
                            val ly = light / 65536
                            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
                            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx.toFloat(), ly.toFloat())
                            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
                        }
                        RenderStats.noteMergeBucket(bufferList.size)
                        uploader.drawMultiple(bufferList)
                    }
                }
                RenderTypeState.post(pass)

                if (clearAfterDraw) {
                    it.remove()
                }
            }
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
        }
    }

    private fun isBloomPass(pass: RenderPass): Boolean {
        return pass == RenderPass.BLOOM || pass == RenderPass.BLOOM_TRANSPARENT
    }
}
