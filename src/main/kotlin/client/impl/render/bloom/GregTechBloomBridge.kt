package github.kasuminova.prototypemachinery.client.impl.render.bloom

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.impl.render.RenderManager
import net.minecraftforge.fml.common.Loader

/**
 * Optional integration with GregTech's bloom post-processing pipeline.
 */
internal object GregTechBloomBridge {

    private const val MODID_GREGTECH = "gregtech"

    private const val MODID_LUMENIZED = "lumenized"

    val isEnabled: Boolean
        get() = Loader.isModLoaded(MODID_GREGTECH) || Loader.isModLoaded(MODID_LUMENIZED)

    fun initIfPresent() {
        if (!isEnabled) {
            // Keep render path intact: we simply won't defer bloom to GT post-processing.
            // If something queued bloom work, render it in PM's own pipeline.
            if (RenderManager.hasPendingBloomWork()) {
                PrototypeMachinery.logger.debug("[PM] GT/Lumenized bloom bridge inactive (gregtech/lumenized not loaded).")
            }
            return
        }

        // Register GT bloom callback once. All bloom-capable passes will be rendered via RenderManager.
        GregTechBloomHooks.ensureRegistered()
    }
}
