package github.kasuminova.prototypemachinery.client.impl.render

import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Safety flush for the batched render queue.
 *
 * In the current architecture:
 * - DEFAULT and TRANSPARENT passes are rendered directly in TESR
 * - BLOOM passes are deferred to RenderManager only when GT bloom is enabled
 * - GT bloom callback (runs before RenderWorldLastEvent) handles BLOOM passes
 *
 * This handler serves as a safety net to flush any remaining buffers that might
 * have been queued but not yet drawn. In normal operation, the RenderManager
 * should already be empty by this point.
 */
internal object WorldRenderFlushHandler {

    @SubscribeEvent
    internal fun onRenderWorldLast(event: RenderWorldLastEvent) {
        // Safety flush: draw any remaining queued passes.
        // Normally empty because:
        // - DEFAULT/TRANSPARENT are rendered in TESR
        // - BLOOM passes are handled by GT bloom callback (which runs earlier)
        RenderManager.drawAll()
    }
}
