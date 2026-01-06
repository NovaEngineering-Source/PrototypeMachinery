package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.util.BufferBuilderPool
import github.kasuminova.prototypemachinery.client.util.DirectByteBufferPool
import net.minecraft.client.renderer.BufferBuilder

/**
 * CPU-side buffers built off-thread, ready to be enqueued into the render manager.
 *
 * IMPORTANT: The contained [BufferBuilder] instances must only be *drawn* on the render thread.
 */
internal data class BuiltBuffers(
    /**
     * World-space origin used when baking vertex positions.
     *
     * Geometry in buffers is expressed relative to this origin.
     * Draw code must translate by this origin (in addition to the usual -camera).
     */
    internal val originX: Int = 0,
    internal val originY: Int = 0,
    internal val originZ: Int = 0,
    internal val byPass: Map<RenderPass, BufferBuilder> = emptyMap(),
    internal val packedByPass: Map<RenderPass, PackedBucketBatch> = emptyMap(),
    internal val gpuByPass: Map<RenderPass, GpuBucketDraw> = emptyMap(),
) {
    internal fun isEmpty(): Boolean = byPass.isEmpty() && packedByPass.isEmpty() && gpuByPass.isEmpty()

    /**
     * Recycle all contained [BufferBuilder] instances back to the global pool.
     *
     * IMPORTANT: Only call this when these buffers will no longer be drawn.
     */
    internal fun disposeToPool() {
        if (byPass.isNotEmpty()) {
            for (b in byPass.values) {
                BufferBuilderPool.recycle(b)
            }
        }

        if (packedByPass.isNotEmpty()) {
            for (batch in packedByPass.values) {
                for (p in batch.parts) {
                    try {
                        DirectByteBufferPool.recycle(p.data)
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }
        }

        if (gpuByPass.isNotEmpty()) {
            for (d in gpuByPass.values) {
                try {
                    d.dispose?.invoke()
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }
}
