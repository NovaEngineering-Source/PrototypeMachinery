package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.util.BufferBuilderPool
import github.kasuminova.prototypemachinery.client.util.DirectByteBufferPool
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.vertex.VertexFormat
import java.nio.ByteBuffer
import java.util.concurrent.RecursiveAction
import java.util.concurrent.atomic.AtomicInteger

internal data class PackedBucket(
    val format: VertexFormat,
    val drawMode: Int,
    val vertexCount: Int,
    val totalBytes: Int,
    val data: ByteBuffer,
)

/**
 * A packed bucket may be split into a few parts to reduce peak allocation and improve packing locality.
 *
 * All parts share the same [format] and [drawMode]. Callers should draw parts sequentially.
 */
internal data class PackedBucketBatch(
    val format: VertexFormat,
    val drawMode: Int,
    val parts: List<PackedBucket>,
    val totalVertexCount: Int,
    val totalBytes: Int,
)

/**
 * Background task that packs a list of compatible [BufferBuilder]s into a single contiguous direct [ByteBuffer].
 *
 * IMPORTANT:
 * - GL upload/draw must still happen on the render thread.
 * - Callers should pin source builders before submitting, and this task will unpin them on completion.
 * - If [generationRef] changes before completion (e.g., world unload), the produced buffer is recycled.
 */
internal class BucketMergeTask(
    private val builders: List<BufferBuilder>,
    private val tag: String,
    private val generationRef: AtomicInteger,
    private val expectedGeneration: Int,
) : RecursiveAction() {

    private data class CopySegment(
        val builder: BufferBuilder,
        val srcOffsetBytes: Int,
        val destOffsetBytes: Int,
        val lengthBytes: Int,
    )

    private class ParallelCopyAction(
        private val dest: ByteBuffer,
        private val segments: Array<CopySegment>,
        private val start: Int,
        private val end: Int,
    ) : RecursiveAction() {
        override fun compute() {
            val count = end - start
            if (count <= 16) {
                val dst = dest.duplicate()
                for (i in start until end) {
                    val s = segments[i]
                    if (s.lengthBytes <= 0) continue

                    val src = s.builder.byteBuffer.duplicate()
                    src.clear()
                    src.position(s.srcOffsetBytes)
                    src.limit(s.srcOffsetBytes + s.lengthBytes)

                    dst.clear()
                    dst.position(s.destOffsetBytes)
                    dst.limit(s.destOffsetBytes + s.lengthBytes)
                    dst.put(src)
                }
                return
            }

            val mid = (start + end) ushr 1
            invokeAll(
                ParallelCopyAction(dest, segments, start, mid),
                ParallelCopyAction(dest, segments, mid, end),
            )
        }
    }

    @Volatile
    internal var result: PackedBucketBatch? = null
        private set

    @Volatile
    internal var error: Throwable? = null
        private set

    override fun compute() {
        val outs: MutableList<ByteBuffer> = ArrayList(4)
        try {
            if (builders.isEmpty()) return

            val first = builders[0]
            val format = first.vertexFormat
            val drawMode = first.drawMode
            val vertexSize = format.size

            val maxPartBytesTuning = RenderTuning.asyncUncachedBucketPackMaxPartBytes
            val maxPartsTuning = RenderTuning.asyncUncachedBucketPackMaxParts
            val maxPartBytes = if (maxPartBytesTuning <= 0) Int.MAX_VALUE else maxPartBytesTuning
            val maxParts = if (maxPartsTuning < 1) 1 else maxPartsTuning

            data class PartBuild(
                val segments: MutableList<CopySegment>,
                var bytes: Int,
                var vertexCount: Int,
            )

            val partsTmp: MutableList<PartBuild> = ArrayList(4)
            var curSegs: MutableList<CopySegment> = ArrayList(32)
            var curBytes = 0
            var curVertices = 0

            fun flushPartIfNonEmpty() {
                if (curBytes <= 0 || curVertices <= 0 || curSegs.isEmpty()) return
                partsTmp.add(PartBuild(curSegs, curBytes, curVertices))
                curSegs = ArrayList(32)
                curBytes = 0
                curVertices = 0
            }

            var totalBytes = 0
            var totalVertexCount = 0

            // Build parts with segments (may split within a builder, aligned to vertex boundaries).
            for (b in builders) {
                val vc = b.vertexCount
                if (vc <= 0) continue
                if (b.drawMode != drawMode || b.vertexFormat != format) {
                    return
                }

                val bytesTotal = vc * vertexSize
                if (bytesTotal <= 0) continue

                var remaining = bytesTotal
                var srcOffset = 0
                while (remaining > 0) {
                    // Start a new part if this chunk would exceed maxPartBytes and we still can.
                    if (curBytes > 0 && (curBytes >= maxPartBytes || curBytes + remaining > maxPartBytes) && partsTmp.size < (maxParts - 1)) {
                        flushPartIfNonEmpty()
                    }

                    var budget = maxPartBytes - curBytes
                    if (budget <= 0 && partsTmp.size < (maxParts - 1)) {
                        flushPartIfNonEmpty()
                        budget = maxPartBytes
                    }

                    var chunk = remaining
                    if (budget > 0 && budget < chunk) chunk = budget

                    // Keep progress even if tuning is too small.
                    if (chunk < vertexSize) chunk = vertexSize

                    // Align to vertex boundary (bytesTotal is always a multiple of vertexSize).
                    val rem = chunk % vertexSize
                    if (rem != 0) chunk -= rem
                    if (chunk <= 0) chunk = vertexSize
                    if (chunk > remaining) chunk = remaining

                    curSegs.add(
                        CopySegment(
                            builder = b,
                            srcOffsetBytes = srcOffset,
                            destOffsetBytes = curBytes,
                            lengthBytes = chunk,
                        )
                    )
                    curBytes += chunk
                    curVertices += (chunk / vertexSize)

                    srcOffset += chunk
                    remaining -= chunk

                    // If we filled the part, flush if allowed to create more parts.
                    if (curBytes >= maxPartBytes && partsTmp.size < (maxParts - 1)) {
                        flushPartIfNonEmpty()
                    }
                }

                totalBytes += bytesTotal
                totalVertexCount += vc
            }

            flushPartIfNonEmpty()

            if (totalVertexCount <= 0 || totalBytes <= 0) return
            if (partsTmp.isEmpty()) return

            val packedParts: MutableList<PackedBucket> = ArrayList(partsTmp.size)

            for (p in partsTmp) {
                val partBytes = p.bytes
                val partVertices = p.vertexCount
                if (partBytes <= 0 || partVertices <= 0) continue

                val out = DirectByteBufferPool.borrow(partBytes, tag = tag)
                outs.add(out)
                out.clear()
                out.limit(partBytes)

                val segments = p.segments.toTypedArray()
                val doParallel = RenderTuning.mergeParallelCopyEnabled &&
                    partBytes >= RenderTuning.mergeParallelCopyMinBytes &&
                    segments.size >= RenderTuning.mergeParallelCopyMinSegments &&
                    RenderTaskExecutor.pool.parallelism > 1

                if (doParallel && segments.size >= 2) {
                    RenderTaskExecutor.pool.invoke(ParallelCopyAction(out, segments, 0, segments.size))
                } else {
                    for (s in segments) {
                        if (s.lengthBytes <= 0) continue
                        val src = s.builder.byteBuffer.duplicate()
                        src.clear()
                        src.position(s.srcOffsetBytes)
                        src.limit(s.srcOffsetBytes + s.lengthBytes)
                        out.position(s.destOffsetBytes)
                        out.put(src)
                    }
                }

                out.position(partBytes)
                out.flip()

                packedParts.add(
                    PackedBucket(
                        format = format,
                        drawMode = drawMode,
                        vertexCount = partVertices,
                        totalBytes = partBytes,
                        data = out,
                    )
                )
            }

            if (packedParts.isEmpty()) return

            // If the world/generation changed while we were building, drop outputs eagerly.
            if (generationRef.get() != expectedGeneration) {
                for (b in packedParts) {
                    try {
                        DirectByteBufferPool.recycle(b.data)
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
                outs.clear()
                return
            }

            result = PackedBucketBatch(
                format = format,
                drawMode = drawMode,
                parts = packedParts,
                totalVertexCount = totalVertexCount,
                totalBytes = totalBytes,
            )
            outs.clear()
        } catch (t: Throwable) {
            error = t
            for (b in outs) {
                try {
                    DirectByteBufferPool.recycle(b)
                } catch (_: Throwable) {
                    // ignore
                }
            }
        } finally {
            // Always unpin sources.
            for (b in builders) {
                try {
                    BufferBuilderPool.unpin(b)
                } catch (_: Throwable) {
                    // ignore
                }
            }
        }
    }
}
