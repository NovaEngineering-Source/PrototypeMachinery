package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.client.util.DirectByteBufferPool
import net.minecraft.client.renderer.vertex.VertexFormat
import java.nio.ByteBuffer
import java.nio.IntBuffer

/**
 * A reusable abstraction for "write packed vertices into some native memory".
 *
 * Current implementation is backed by pooled direct buffers, but this is designed to also support
 * mapped VBO ranges (render thread maps/unmaps; build threads only write).
 */
internal interface VertexWriteTarget : AutoCloseable {

    /** Backing native memory view. */
    val byteBuffer: ByteBuffer

    /** Int view of [byteBuffer]. Callers should use absolute puts or manage position carefully. */
    val intBuffer: IntBuffer

    /**
     * Finalize the written data into a [PackedBucketBatch].
     *
     * The implementation may recycle/unmap the underlying memory on failure.
     */
    fun sealToPackedBucketBatch(format: VertexFormat, drawMode: Int): PackedBucketBatch?
}

/**
 * Default target: a pooled direct ByteBuffer + IntBuffer view.
 */
internal class PooledDirectVertexWriteTarget private constructor(
    override val byteBuffer: ByteBuffer,
    override val intBuffer: IntBuffer,
) : VertexWriteTarget {

    override fun sealToPackedBucketBatch(format: VertexFormat, drawMode: Int): PackedBucketBatch? {
        val ints = intBuffer.position()
        if (ints <= 0) {
            close()
            return null
        }

        val bytes = ints * 4
        val vertexSize = format.size
        val vertexCount = if (vertexSize > 0) bytes / vertexSize else 0
        if (vertexCount <= 0) {
            close()
            return null
        }

        byteBuffer.clear()
        byteBuffer.limit(bytes)
        byteBuffer.position(0)

        val part = PackedBucket(
            format = format,
            drawMode = drawMode,
            vertexCount = vertexCount,
            totalBytes = bytes,
            data = byteBuffer,
        )

        // Ownership of byteBuffer transfers to PackedBucketBatch; it will be recycled via disposeToPool().
        return PackedBucketBatch(
            format = format,
            drawMode = drawMode,
            parts = listOf(part),
            totalVertexCount = vertexCount,
            totalBytes = bytes,
        )
    }

    override fun close() {
        DirectByteBufferPool.recycle(byteBuffer)
    }

    internal companion object {
        internal fun borrow(minBytes: Int, tag: String): PooledDirectVertexWriteTarget {
            val buf = DirectByteBufferPool.borrow(minBytes, tag = tag)
            buf.clear()
            val ints = buf.asIntBuffer()
            return PooledDirectVertexWriteTarget(buf, ints)
        }
    }
}
