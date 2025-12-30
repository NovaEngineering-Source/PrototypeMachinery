package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GLAllocation
import java.nio.ByteBuffer

/**
 * Central allocation helpers for native/direct buffers created by PM code.
 *
 * Why:
 * - When "heap looks fine but RSS keeps growing", the culprit is often direct buffers or native allocations.
 * - JVM cannot globally intercept all off-heap allocations, but we can at least unify and instrument
 *   the allocation points we control.
 */
internal object NativeBuffers {

    /** Enable expensive stack trace logging for large allocations. */
    private val debugStacks: Boolean = java.lang.Boolean.getBoolean("pm.debug.nativeBuffers")

    /** Log stack traces only if allocation size >= threshold. */
    private val debugThresholdBytes: Int = Integer.getInteger("pm.debug.nativeBuffers.threshold", 4 * 1024 * 1024)

    internal fun createDirectByteBuffer(size: Int, tag: String? = null): ByteBuffer {
        NativeBufferStats.onDirectAlloc(size, tag)

        if (debugStacks && size >= debugThresholdBytes) {
            val t = Throwable("Direct buffer allocation: ${size} bytes${tag?.let { " tag=$it" } ?: ""}")
            PrototypeMachinery.logger.warn("[NativeBuffers] Large direct allocation: {} bytes, tag={}", size, tag, t)
        }

        return GLAllocation.createDirectByteBuffer(size)
    }

    internal fun newBufferBuilder(initialSize: Int, tag: String? = null): BufferBuilder {
        // In 1.12, BufferBuilder internally allocates a direct ByteBuffer of ~initialSize.
        // We can't intercept its internal allocation, but counting call sites is still useful.
        NativeBufferStats.onBufferBuilderRequest(initialSize, tag)
        NativeBufferStats.onBufferBuilderNew(initialSize, tag)

        if (debugStacks && initialSize >= debugThresholdBytes) {
            val t = Throwable("BufferBuilder allocation request: ${initialSize} bytes${tag?.let { " tag=$it" } ?: ""}")
            PrototypeMachinery.logger.warn("[NativeBuffers] Large BufferBuilder init: {} bytes, tag={}", initialSize, tag, t)
        }

        // MC 1.12 BufferBuilder(int) expects an INT count; the direct buffer capacity is (ints * 4) bytes.
        val initInts = if (initialSize <= 0) 0 else ((initialSize + 3) ushr 2)
        return BufferBuilder(initInts)
    }
}
