package github.kasuminova.prototypemachinery.client.impl.render.bloom

import github.kasuminova.prototypemachinery.client.impl.render.RenderManager
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
        // GT may invoke this twice per frame for post-processing. RenderManager clears bloom buckets
        // on first draw, so subsequent invocations become a cheap no-op.
        GlStateManager.pushMatrix()
        try {
            RenderManager.drawBloomPasses(clearAfterDraw = true)
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
        }
    }
}
