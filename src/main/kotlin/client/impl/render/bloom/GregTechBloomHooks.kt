package github.kasuminova.prototypemachinery.client.impl.render.bloom

import github.kasuminova.prototypemachinery.client.impl.render.RenderManager
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomHooks.renderBloomEffect
import gregtech.client.renderer.IRenderSetup
import gregtech.client.shader.postprocessing.BloomType
import gregtech.client.utils.BloomEffectUtil
import gregtech.client.utils.EffectRenderContext
import gregtech.client.utils.IBloomEffect
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.texture.TextureMap
import java.util.function.Predicate

/**
 * GTCEu bloom integration implementation.
 *
 * Important: this object must only be classloaded when the GregTech mod is present.
 * `GregTechBloomBridge` performs the Loader check and calls into this class only when safe.
 */
internal object GregTechBloomHooks : IRenderSetup, IBloomEffect {

    @Volatile
    private var registered: Boolean = false

    /**
     * BloomEffectUtil may invoke [renderBloomEffect] twice per frame.
     *
     * MMCE toggles a flag to distinguish between the two phases.
     * If we clear queued bloom buffers on the first invocation, the second phase will have
     * nothing to process/composite, resulting in "emissive only" (no actual bloom).
     */
    @Volatile
    private var postProcessingPhase: Boolean = false

    fun ensureRegistered() {
        if (registered) return
        registered = true

        // Register a global bloom callback.
        BloomEffectUtil.registerBloomRender(
            this,
            BloomType.UNREAL,
            this,
            Predicate { true }
        )
    }

    override fun preDraw(bufferBuilder: BufferBuilder) {
        // RenderManager draws buffers that are already built; it manages its own GL state.
    }

    override fun postDraw(bufferBuilder: BufferBuilder) {
        // no-op
    }

    override fun shouldRenderBloomEffect(context: EffectRenderContext): Boolean {
        return RenderManager.hasPendingBloomWork()
    }

    override fun renderBloomEffect(bufferBuilder: BufferBuilder, ctx: EffectRenderContext) {
        GlStateManager.pushMatrix()
        try {
            // Draw bloom buffers in both phases; clear only after the post-processing phase.
            RenderManager.drawBloomPasses(clearAfterDraw = postProcessingPhase)
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
        }

        // Toggle after draw so the first invocation renders without clearing.
        postProcessingPhase = !postProcessingPhase
    }
}
