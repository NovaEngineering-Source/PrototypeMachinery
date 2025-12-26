package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import net.minecraft.client.renderer.BufferBuilder

/**
 * CPU-side buffers built off-thread, ready to be enqueued into the render manager.
 *
 * IMPORTANT: The contained [BufferBuilder] instances must only be *drawn* on the render thread.
 */
internal data class BuiltBuffers(
    internal val byPass: Map<RenderPass, BufferBuilder> = emptyMap(),
) {
    internal fun isEmpty(): Boolean = byPass.isEmpty()
}
