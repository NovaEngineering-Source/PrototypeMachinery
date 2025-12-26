package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import org.lwjgl.opengl.GL11

/**
 * GL state transitions for each [RenderPass].
 *
 * This mirrors the idea of MMCE's RenderType enum, but kept independent for PrototypeMachinery.
 */
internal object RenderTypeState {

    private var lastBrightnessX: Float = 0f
    private var lastBrightnessY: Float = 0f

    fun pre(pass: RenderPass) {
        when (pass) {
            RenderPass.DEFAULT -> Unit
            RenderPass.TRANSPARENT -> {
                // Enable alpha blending for transparent objects
                GlStateManager.enableBlend()
                GlStateManager.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA)
                // Disable depth writing so transparent faces don't occlude each other incorrectly
                GlStateManager.depthMask(false)
                // Use LEQUAL to allow overlapping transparent geometry at the same depth
                GlStateManager.depthFunc(GL11.GL_LEQUAL)
            }
            RenderPass.BLOOM -> {
                lastBrightnessX = OpenGlHelper.lastBrightnessX
                lastBrightnessY = OpenGlHelper.lastBrightnessY
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f)
            }
            RenderPass.BLOOM_TRANSPARENT -> {
                pre(RenderPass.BLOOM)
                pre(RenderPass.TRANSPARENT)
            }
        }
    }

    fun post(pass: RenderPass) {
        when (pass) {
            RenderPass.DEFAULT -> Unit
            RenderPass.TRANSPARENT -> {
                // Restore default depth function (Minecraft uses LEQUAL by default)
                GlStateManager.depthFunc(GL11.GL_LEQUAL)
                GlStateManager.depthMask(true)
                GlStateManager.disableBlend()
            }
            RenderPass.BLOOM -> {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastBrightnessX, lastBrightnessY)
            }
            RenderPass.BLOOM_TRANSPARENT -> {
                post(RenderPass.TRANSPARENT)
                post(RenderPass.BLOOM)
            }
        }
    }
}
