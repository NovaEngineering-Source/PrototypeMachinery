package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import github.kasuminova.prototypemachinery.client.impl.render.RenderStress
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskExecutor
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.WorldVertexBufferUploader
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.client.renderer.vertex.VertexFormatElement
import org.lwjgl.opengl.ARBMapBufferRange
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLContext
import java.nio.ByteBuffer
import java.util.concurrent.RecursiveAction

/**
 * A WorldVertexBufferUploader that reuses a single direct buffer for batched draws.
 *
 * Notes:
 * - This implementation assumes all merged builders share the same draw mode and vertex format.
 * - Primary purpose is to reduce draw call overhead; memcpy/merge cost may still be significant.
 */
public class ReusableVboUploader : WorldVertexBufferUploader() {

    private fun mergeMinBuffers(): Int = RenderTuning.mergeMinBuffers
    private fun mergeMinBytes(): Int = RenderTuning.mergeMinBytes
    private fun mergeForceClientArrays(): Boolean = RenderTuning.mergeForceClientArrays

    private fun mergeDirectVboSliceUploadEnabled(): Boolean = RenderTuning.mergeDirectVboSliceUploadEnabled
    private fun mergeDirectVboSliceUploadMinBytes(): Int = RenderTuning.mergeDirectVboSliceUploadMinBytes
    private fun mergeDirectVboSliceUploadMinSegments(): Int = RenderTuning.mergeDirectVboSliceUploadMinSegments

    private fun vboCacheEnabled(): Boolean = RenderTuning.vboCacheEnabled

    private var scratch: ByteBuffer? = null

    // Optional VBO path for merged draws.
    // We keep a small ring of VBOs to reduce "upload into same buffer while previous draws still reference it" stalls.
    private var scratchVboRing: Array<VertexBuffer?> = emptyArray()
    private var scratchVboRingIndex: Int = 0
    private var scratchVboRingSize: Int = 0
    private var scratchVboFormat: VertexFormat? = null

    private data class ScratchVboSlot(
        val vbo: VertexBuffer,
        val index: Int,
    )

    // Cached GL capabilities for feature detection (render thread only).
    private var cachedCaps: org.lwjgl.opengl.ContextCapabilities? = null

    private data class CopySegment(
        val builder: BufferBuilder,
        val offsetBytes: Int,
        val lengthBytes: Int,
    )

    internal data class ByteBufferSegment(
        val data: ByteBuffer,
        val offsetBytes: Int,
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
            // Tune: small enough to keep overhead bounded.
            if (count <= 16) {
                val dst = dest.duplicate()
                for (i in start until end) {
                    val s = segments[i]
                    if (s.lengthBytes <= 0) continue

                    val src = s.builder.byteBuffer.duplicate()
                    src.clear()
                    src.limit(s.lengthBytes)

                    dst.clear()
                    dst.position(s.offsetBytes)
                    dst.limit(s.offsetBytes + s.lengthBytes)
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

    private fun copyIntoScratch(
        segments: Array<CopySegment>,
        totalBytes: Int,
        parallel: Boolean,
    ): ByteBuffer {
        ensureScratch(totalBytes)
        val scratchBuf = scratch!!

        scratchBuf.clear()
        scratchBuf.limit(totalBytes)

        if (parallel && segments.size >= 2 && RenderTaskExecutor.pool.parallelism > 1) {
            RenderTaskExecutor.pool.invoke(ParallelCopyAction(scratchBuf, segments, 0, segments.size))
        } else {
            // Sequential copy.
            for (s in segments) {
                if (s.lengthBytes <= 0) continue
                val src = s.builder.byteBuffer.duplicate()
                src.clear()
                src.limit(s.lengthBytes)

                scratchBuf.position(s.offsetBytes)
                scratchBuf.put(src)
            }
        }

        // Prepare for reading/uploading.
        scratchBuf.position(totalBytes)
        scratchBuf.flip()
        return scratchBuf
    }

    internal data class MergeInfo(
        val format: VertexFormat,
        val drawMode: Int,
        val vertexCount: Int,
        val totalBytes: Int,
        val data: ByteBuffer,
    )

    /**
     * Explicitly release any GL/VBO resources held by this uploader.
     *
     * Call this on the main/render thread when the world is unloaded or resources are reloaded.
     */
    public fun dispose() {
        for (v in scratchVboRing) {
            try {
                v?.deleteGlBuffers()
            } catch (_: Throwable) {
                // ignore
            }
        }
        scratchVboRing = emptyArray()
        scratchVboRingIndex = 0
        scratchVboRingSize = 0
        scratchVboFormat = null
        scratch = null
    }

    private fun acquireScratchVbo(format: VertexFormat): ScratchVboSlot {
        val desiredSize = RenderTuning.mergeScratchVboRingSize.coerceIn(1, 64)
        if (scratchVboFormat != format || scratchVboRingSize != desiredSize || scratchVboRing.isEmpty()) {
            for (v in scratchVboRing) {
                try {
                    v?.deleteGlBuffers()
                } catch (_: Throwable) {
                    // ignore
                }
            }
            scratchVboRing = arrayOfNulls(desiredSize)
            scratchVboRingSize = desiredSize
            scratchVboRingIndex = 0
            scratchVboFormat = format
        }

        // Pick next slot from the ring.
        // Synchronization is handled implicitly by orphaning (glBufferData) before upload.
        val idx = scratchVboRingIndex
        scratchVboRingIndex = if (idx + 1 >= scratchVboRingSize) 0 else (idx + 1)

        val existing = scratchVboRing[idx]
        if (existing != null) return ScratchVboSlot(existing, idx)

        val created = VertexBuffer(format)
        scratchVboRing[idx] = created
        return ScratchVboSlot(created, idx)
    }

    private fun glCaps(): org.lwjgl.opengl.ContextCapabilities {
        val c = cachedCaps
        if (c != null) return c
        val caps = GLContext.getCapabilities()
        cachedCaps = caps
        return caps
    }

    private fun supportsMapBufferRange(): Boolean {
        val caps = glCaps()
        return caps.OpenGL30 || caps.GL_ARB_map_buffer_range
    }

    private fun glMapBufferRangeCompat(target: Int, offset: Long, length: Long, access: Int): ByteBuffer? {
        val caps = glCaps()
        return if (caps.OpenGL30) {
            GL30.glMapBufferRange(target, offset, length, access, null as ByteBuffer?)
        } else if (caps.GL_ARB_map_buffer_range) {
            ARBMapBufferRange.glMapBufferRange(target, offset, length, access, null as ByteBuffer?)
        } else {
            null
        }
    }

    /**
     * Merge a list of compatible builders into the internal scratch buffer.
     *
     * The [block] receives a [MergeInfo] whose [MergeInfo.data] is backed by the uploader's internal scratch buffer.
     * Do NOT escape/store that ByteBuffer reference outside the callback.
     *
     * Returns false if builders are incompatible or empty.
     */
    internal inline fun withMergedScratch(builders: List<BufferBuilder>, block: (MergeInfo) -> Unit): Boolean {
        if (builders.isEmpty()) return false

        val first = builders[0]
        val format: VertexFormat = first.vertexFormat
        val drawMode = first.drawMode

        var totalBytes = 0
        var totalVertexCount = 0

        val vertexSize = format.size

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            if (b.drawMode != drawMode || b.vertexFormat != format) {
                return false
            }
            totalBytes += b.vertexCount * vertexSize
            totalVertexCount += b.vertexCount
        }

        if (totalVertexCount <= 0 || totalBytes <= 0) return false

        ensureScratch(totalBytes)
        val scratchBuf = scratch!!

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            val bytesUsed = b.vertexCount * vertexSize
            val src = b.byteBuffer.duplicate()
            src.clear()
            src.limit(bytesUsed)
            scratchBuf.put(src)
        }

        scratchBuf.flip()

        try {
            block(
                MergeInfo(
                    format = format,
                    drawMode = drawMode,
                    vertexCount = totalVertexCount,
                    totalBytes = totalBytes,
                    data = scratchBuf,
                )
            )
        } finally {
            // Caller is done; restore scratch for next use.
            scratchBuf.clear()
            scratchBuf.limit(totalBytes)
        }

        return true
    }

    /**
     * Draw a VBO that uses the given vertex [format].
     *
     * The VBO must already contain valid data for the format.
     */
    internal fun drawVbo(vbo: VertexBuffer, format: VertexFormat, drawMode: Int, vertexCount: Int) {
        if (vertexCount <= 0) return

        vbo.bindBuffer()
        setupPointersForFormat(format)

        val repeats = RenderStress.drawMultiplier
        RenderStats.addDraw(repeats, vertexCount * repeats)
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(drawMode, 0, vertexCount)
        }

        teardownPointersForFormat(format)
        vbo.unbindBuffer()
    }

    /**
     * Draw using client-side arrays, explicitly bypassing per-BufferBuilder VBO cache.
     *
     * This is useful for dynamic/animated geometry where per-buffer caching would churn and
     * cause excessive glBufferData traffic.
     */
    internal fun drawWithoutVboCache(builder: BufferBuilder) {
        if (builder.vertexCount <= 0) return
        drawWithClientArrays(builder)
    }

    private fun drawWithClientArrays(builder: BufferBuilder) {
        val buf = builder.byteBuffer

        val format = builder.vertexFormat
        val vertexSize = format.size
        val elements = format.elements

        // Pre-draw setup
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            buf.position(format.getOffset(i))
            element.usage.preDraw(format, i, vertexSize, buf)
        }

        val repeats = RenderStress.drawMultiplier
        RenderStats.addDraw(repeats, builder.vertexCount * repeats)
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(builder.drawMode, 0, builder.vertexCount)
        }

        // Post-draw cleanup
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            element.usage.postDraw(format, i, vertexSize, buf)
        }

        // Reset buffer position to 0 to ensure it's ready for the next draw call (or next frame).
        // This is critical when reusing BufferBuilders across frames, as preDraw modifies the position.
        buf.position(0)
    }

    override fun draw(builder: BufferBuilder) {
        if (builder.vertexCount <= 0) return

        // Fast path: draw from a cached per-buffer VBO (no per-frame upload).
        if (vboCacheEnabled() && !mergeForceClientArrays() && OpenGlHelper.useVbo()) {
            val entry = BufferBuilderVboCache.getOrCreate(builder)
            if (entry != null) {
                OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, entry.vboId)
                setupPointersForFormat(entry.format)

                val repeats = RenderStress.drawMultiplier
                RenderStats.addDraw(repeats, entry.vertexCount * repeats)
                for (i in 0 until repeats) {
                    GlStateManager.glDrawArrays(entry.drawMode, 0, entry.vertexCount)
                }

                teardownPointersForFormat(entry.format)
                OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0)
                return
            }
        }

        drawWithClientArrays(builder)
    }

    /**
     * Draw multiple buffers while explicitly avoiding per-buffer VBO cache.
     *
     * Behavior:
     * - If merge is worthwhile and VBO is available, merges into scratch and uploads once (scratch VBO).
     * - Otherwise draws individually via client arrays.
     */
    public fun drawMultipleWithoutVboCache(builders: List<BufferBuilder>) {
        if (builders.isEmpty()) return
        if (builders.size == 1) {
            drawWithoutVboCache(builders[0])
            return
        }

        val first = builders[0]
        val format: VertexFormat = first.vertexFormat
        val drawMode = first.drawMode

        var totalBytes = 0
        var totalVertexCount = 0
        val vertexSize = format.size

        // Build segment list with prefix-sum offsets.
        val segTmp = ArrayList<CopySegment>(builders.size)
        var runningOffset = 0
        var considered = 0

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            if (b.drawMode != drawMode || b.vertexFormat != format) {
                builders.forEach { drawWithoutVboCache(it) }
                return
            }
            val bytes = b.vertexCount * vertexSize
            segTmp.add(CopySegment(builder = b, offsetBytes = runningOffset, lengthBytes = bytes))
            runningOffset += bytes
            totalBytes += bytes
            totalVertexCount += b.vertexCount
            considered++
        }

        if (totalVertexCount <= 0) return

        if (builders.size < mergeMinBuffers() || totalBytes < mergeMinBytes()) {
            builders.forEach { drawWithoutVboCache(it) }
            return
        }

        RenderStats.addMerge(builders.size, totalBytes)

        val segments = segTmp.toTypedArray()
        if (!mergeForceClientArrays() && OpenGlHelper.useVbo()) {
            val useSlice = mergeDirectVboSliceUploadEnabled() &&
                totalBytes >= mergeDirectVboSliceUploadMinBytes() &&
                segments.size >= mergeDirectVboSliceUploadMinSegments()

            RenderStats.addDraw(RenderStress.drawMultiplier, totalVertexCount * RenderStress.drawMultiplier)

            if (useSlice) {
                drawMergedSegmentsWithVbo(format, drawMode, totalVertexCount, totalBytes, segments)
                return
            }

            val doParallel = RenderTuning.mergeParallelCopyEnabled &&
                totalBytes >= RenderTuning.mergeParallelCopyMinBytes &&
                segments.size >= RenderTuning.mergeParallelCopyMinSegments

            val scratchBuf = copyIntoScratch(segments, totalBytes, parallel = doParallel)
            drawMergedWithVbo(format, drawMode, totalVertexCount, scratchBuf)
            scratchBuf.clear()
            scratchBuf.limit(totalBytes)
            return
        }

        val doParallel = RenderTuning.mergeParallelCopyEnabled &&
            totalBytes >= RenderTuning.mergeParallelCopyMinBytes &&
            segments.size >= RenderTuning.mergeParallelCopyMinSegments

        val scratchBuf = copyIntoScratch(segments, totalBytes, parallel = doParallel)

        val elements = format.elements
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            scratchBuf.position(format.getOffset(i))
            element.usage.preDraw(format, i, vertexSize, scratchBuf)
        }

        val repeats = RenderStress.drawMultiplier
        RenderStats.addDraw(repeats, totalVertexCount * repeats)
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(drawMode, 0, totalVertexCount)
        }

        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            element.usage.postDraw(format, i, vertexSize, scratchBuf)
        }

        scratchBuf.clear()
        scratchBuf.limit(totalBytes)
    }

    /**
     * Draw a pre-packed merged buffer.
     *
     * This is intended for async/off-thread packing: the caller provides a contiguous [data] buffer
     * (position=0, limit=totalBytes) and we only do the GL upload + draw on the render thread.
     */
    internal fun drawMergedByteBuffer(format: VertexFormat, drawMode: Int, vertexCount: Int, totalBytes: Int, data: ByteBuffer) {
        if (vertexCount <= 0 || totalBytes <= 0) return

        val buf = data.duplicate().also {
            it.clear()
            it.limit(totalBytes)
        }

        if (!mergeForceClientArrays() && OpenGlHelper.useVbo()) {
            RenderStats.addDraw(RenderStress.drawMultiplier, vertexCount * RenderStress.drawMultiplier)
            drawMergedWithVbo(format, drawMode, vertexCount, buf)
            return
        }

        val vertexSize = format.size
        val elements = format.elements
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            buf.position(format.getOffset(i))
            element.usage.preDraw(format, i, vertexSize, buf)
        }

        val repeats = RenderStress.drawMultiplier
        RenderStats.addDraw(repeats, vertexCount * repeats)
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(drawMode, 0, vertexCount)
        }

        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            element.usage.postDraw(format, i, vertexSize, buf)
        }
    }

    /**
     * Merges multiple [BufferBuilder] byte buffers into one scratch buffer and draws as a single glDrawArrays.
     *
     * This reduces draw calls but increases memcpy. Consider moving merge to background tasks or using VBO caching.
     */
    public fun drawMultiple(builders: List<BufferBuilder>) {
        if (builders.isEmpty()) return

        val first = builders[0]
        val format: VertexFormat = first.vertexFormat
        val drawMode = first.drawMode

        // Fast path: single buffer, no merge needed.
        if (builders.size == 1) {
            draw(first)
            return
        }

        var totalBytes = 0
        var totalVertexCount = 0

        val vertexSize = format.size

        // Build segment list with prefix-sum offsets so we can optionally upload directly into a mapped VBO.
        val segTmp = ArrayList<CopySegment>(builders.size)
        var runningOffset = 0

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            // Safety: do not merge incompatible formats.
            if (b.drawMode != drawMode || b.vertexFormat != format) {
                // Fallback: draw individually.
                builders.forEach { draw(it) }
                return
            }
            val bytes = b.vertexCount * vertexSize
            segTmp.add(CopySegment(builder = b, offsetBytes = runningOffset, lengthBytes = bytes))
            runningOffset += bytes
            totalBytes += bytes
            totalVertexCount += b.vertexCount
        }

        if (totalVertexCount <= 0) return

        // If per-buffer VBO caching is enabled and the bucket is large, prefer individual draws.
        // This avoids memcpy+merge and avoids uploading a giant merged VBO every frame.
        if (vboCacheEnabled() && !mergeForceClientArrays() && OpenGlHelper.useVbo()) {
            // If most buffers are already cached, merging would just re-upload mostly-static geometry.
            // Prefer drawing individually from cached VBOs in that case.
            val ratioThreshold = RenderTuning.vboCachePreferIndividualWhenCachedRatio
            if (ratioThreshold <= 0.0) {
                builders.forEach { draw(it) }
                return
            }
            if (ratioThreshold < 1.0) {
                var considered = 0
                var cached = 0
                for (b in builders) {
                    if (b.vertexCount <= 0) continue
                    considered++
                    if (BufferBuilderVboCache.peek(b) != null) cached++
                }
                if (considered > 0 && cached.toDouble() / considered.toDouble() >= ratioThreshold) {
                    builders.forEach { draw(it) }
                    return
                }
            }

            val preferAbove = RenderTuning.vboCachePreferIndividualAboveBytes
            if (totalBytes >= preferAbove) {
                builders.forEach { draw(it) }
                return
            }
        }

        // Optional heuristic: for very small buckets, memcpy+merge (and potentially VBO upload)
        // can cost more than just issuing a few extra draws.
        if (builders.size < mergeMinBuffers() || totalBytes < mergeMinBytes()) {
            builders.forEach { draw(it) }
            return
        }

        RenderStats.addMerge(builders.size, totalBytes)

        val segments = segTmp.toTypedArray()

        // Fast path: when VBOs are enabled, avoid the extra builder->scratch memcpy by writing segments
        // directly into the mapped VBO (single map + N puts).
        if (!mergeForceClientArrays() && OpenGlHelper.useVbo()) {
            val useSlice = mergeDirectVboSliceUploadEnabled() &&
                totalBytes >= mergeDirectVboSliceUploadMinBytes() &&
                segments.size >= mergeDirectVboSliceUploadMinSegments()

            RenderStats.addDraw(RenderStress.drawMultiplier, totalVertexCount * RenderStress.drawMultiplier)

            if (useSlice) {
                drawMergedSegmentsWithVbo(format, drawMode, totalVertexCount, totalBytes, segments)
                return
            }

            // Fallback: merge into scratch then upload once.
            val doParallel = RenderTuning.mergeParallelCopyEnabled &&
                totalBytes >= RenderTuning.mergeParallelCopyMinBytes &&
                segments.size >= RenderTuning.mergeParallelCopyMinSegments

            val scratchBuf = copyIntoScratch(segments, totalBytes, parallel = doParallel)
            drawMergedWithVbo(format, drawMode, totalVertexCount, scratchBuf)
            scratchBuf.clear()
            scratchBuf.limit(totalBytes)
            return
        }

        // Client arrays path: we still need a contiguous scratch buffer.
        val doParallel = RenderTuning.mergeParallelCopyEnabled &&
            totalBytes >= RenderTuning.mergeParallelCopyMinBytes &&
            segments.size >= RenderTuning.mergeParallelCopyMinSegments

        val scratchBuf = copyIntoScratch(segments, totalBytes, parallel = doParallel)

        val elements = format.elements

        // Pre-draw setup
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            scratchBuf.position(format.getOffset(i))
            element.usage.preDraw(format, i, vertexSize, scratchBuf)
        }

        val repeats = RenderStress.drawMultiplier
        RenderStats.addDraw(repeats, totalVertexCount * repeats)
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(drawMode, 0, totalVertexCount)
        }

        // Post-draw cleanup
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            element.usage.postDraw(format, i, vertexSize, scratchBuf)
        }

        scratchBuf.clear()
        // Restore our expected limit for the next merge.
        scratchBuf.limit(totalBytes)
    }

    private fun ensureScratch(minSize: Int) {
        val current = scratch
        if (current == null || current.capacity() < minSize) {
            scratch = NativeBuffers.createDirectByteBuffer(minSize, tag = "ReusableVboUploader.scratch")
        }
        scratch!!.limit(minSize)
    }

    private fun drawMergedWithVbo(format: VertexFormat, drawMode: Int, vertexCount: Int, data: ByteBuffer) {
        // Ensure scratch VBO ring matches the format.
        val slot = acquireScratchVbo(format)
        val vbo = slot.vbo

        // Upload to GPU.
        // NOTE: Vanilla VertexBuffer.bufferData always uses GL_STATIC_DRAW (35044), which is often suboptimal
        // for per-frame dynamic uploads. We upload manually here to allow tuning the usage hint.
        RenderStats.addVboUpload(data.remaining())
        vbo.bindBuffer()
        val usage = RenderTuning.mergeScratchVboUsage
        val sanitizedUsage = if (usage == GL15.GL_STATIC_DRAW || usage == GL15.GL_DYNAMIC_DRAW || usage == GL15.GL_STREAM_DRAW) {
            usage
        } else {
            GL15.GL_STREAM_DRAW
        }

        val uploadMode = RenderTuning.mergeScratchVboUploadMode
        val sizeBytes = data.remaining()

        if (uploadMode == RenderTuning.ScratchVboUploadMode.MAP_RANGE_UNSYNC && supportsMapBufferRange()) {
            // Always orphan for this path; we will fill via mapping.
            GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, sizeBytes.toLong(), sanitizedUsage)

            val access = GL30.GL_MAP_WRITE_BIT or GL30.GL_MAP_INVALIDATE_BUFFER_BIT or GL30.GL_MAP_UNSYNCHRONIZED_BIT
            val mapped = glMapBufferRangeCompat(OpenGlHelper.GL_ARRAY_BUFFER, 0L, sizeBytes.toLong(), access)
            if (mapped != null) {
                val src = data.duplicate()
                mapped.clear()
                mapped.limit(sizeBytes)
                mapped.put(src)

                val ok = GL15.glUnmapBuffer(OpenGlHelper.GL_ARRAY_BUFFER)
                if (!ok) {
                    // Data store became corrupt; fall back to subData.
                    val src2 = data.duplicate()
                    GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, 0L, src2)
                }
            } else {
                // Mapping failed at runtime; fall back.
                if (RenderTuning.mergeScratchVboOrphaningEnabled) {
                    GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, sizeBytes.toLong(), sanitizedUsage)
                    val src = data.duplicate()
                    GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, 0L, src)
                } else {
                    val src = data.duplicate()
                    OpenGlHelper.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, src, sanitizedUsage)
                }
            }
        } else {
            if (RenderTuning.mergeScratchVboOrphaningEnabled) {
                // Orphan then upload: tends to reduce stalls for streaming updates.
                GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, sizeBytes.toLong(), sanitizedUsage)
                val src = data.duplicate()
                GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, 0L, src)
            } else {
                val src = data.duplicate()
                OpenGlHelper.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, src, sanitizedUsage)
            }
        }

        // Set pointers with offsets (VBO path).
        setupPointersForFormat(format)

        val repeats = RenderStress.drawMultiplier
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(drawMode, 0, vertexCount)
        }

        teardownPointersForFormat(format)
        vbo.unbindBuffer()
    }

    private fun drawMergedSegmentsWithVbo(
        format: VertexFormat,
        drawMode: Int,
        vertexCount: Int,
        totalBytes: Int,
        segments: Array<CopySegment>,
    ) {
        if (vertexCount <= 0 || totalBytes <= 0) return
        if (segments.isEmpty()) return

        val vbo = acquireScratchVbo(format)
        val slotIndex = vbo.index
        val vboObj = vbo.vbo

        RenderStats.addVboUpload(totalBytes)
        vboObj.bindBuffer()

        val usage = RenderTuning.mergeScratchVboUsage
        val sanitizedUsage = if (usage == GL15.GL_STATIC_DRAW || usage == GL15.GL_DYNAMIC_DRAW || usage == GL15.GL_STREAM_DRAW) {
            usage
        } else {
            GL15.GL_STREAM_DRAW
        }

        val uploadMode = RenderTuning.mergeScratchVboUploadMode
        if (uploadMode == RenderTuning.ScratchVboUploadMode.MAP_RANGE_UNSYNC && supportsMapBufferRange()) {
            // Orphan then fill via mapping.
            GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)
            val access = GL30.GL_MAP_WRITE_BIT or GL30.GL_MAP_INVALIDATE_BUFFER_BIT or GL30.GL_MAP_UNSYNCHRONIZED_BIT
            val mapped = glMapBufferRangeCompat(OpenGlHelper.GL_ARRAY_BUFFER, 0L, totalBytes.toLong(), access)
            if (mapped != null) {
                for (s in segments) {
                    val len = s.lengthBytes
                    if (len <= 0) continue
                    val src = s.builder.byteBuffer.duplicate()
                    src.clear()
                    src.limit(len)
                    mapped.position(s.offsetBytes)
                    mapped.put(src)
                }
                val ok = GL15.glUnmapBuffer(OpenGlHelper.GL_ARRAY_BUFFER)
                if (!ok) {
                    // Fall back to subData slices.
                    GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)
                    for (s in segments) {
                        val len = s.lengthBytes
                        if (len <= 0) continue
                        val src = s.builder.byteBuffer.duplicate()
                        src.clear()
                        src.limit(len)
                        GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, s.offsetBytes.toLong(), src)
                    }
                }
            } else {
                // Mapping failed: fall back to subData slices.
                GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)
                for (s in segments) {
                    val len = s.lengthBytes
                    if (len <= 0) continue
                    val src = s.builder.byteBuffer.duplicate()
                    src.clear()
                    src.limit(len)
                    GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, s.offsetBytes.toLong(), src)
                }
            }
        } else {
            // Allocate storage first (effectively orphaning). We then fill slices with sub-data.
            GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)

            for (s in segments) {
                val len = s.lengthBytes
                if (len <= 0) continue

                val src = s.builder.byteBuffer.duplicate()
                src.clear()
                src.limit(len)

                GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, s.offsetBytes.toLong(), src)
            }
        }

        setupPointersForFormat(format)

        val repeats = RenderStress.drawMultiplier
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(drawMode, 0, vertexCount)
        }

        teardownPointersForFormat(format)
        vboObj.unbindBuffer()
    }

    internal fun drawMergedByteBufferSegments(
        format: VertexFormat,
        drawMode: Int,
        vertexCount: Int,
        totalBytes: Int,
        segments: Array<ByteBufferSegment>,
    ) {
        if (vertexCount <= 0 || totalBytes <= 0) return
        if (segments.isEmpty()) return

        if (mergeForceClientArrays() || !OpenGlHelper.useVbo()) {
            // Client arrays path: just draw each segment individually.
            // (CPU-merge would defeat the point for pre-packed segments.)
            for (s in segments) {
                val len = s.lengthBytes
                if (len <= 0) continue
                val buf = s.data.duplicate().also {
                    it.clear()
                    it.limit(len)
                }
                drawMergedByteBuffer(format, drawMode, len / format.size, len, buf)
            }
            return
        }

        val vboSlot = acquireScratchVbo(format)
        val vbo = vboSlot.vbo

        RenderStats.addVboUpload(totalBytes)
        vbo.bindBuffer()

        val usage = RenderTuning.mergeScratchVboUsage
        val sanitizedUsage = if (usage == GL15.GL_STATIC_DRAW || usage == GL15.GL_DYNAMIC_DRAW || usage == GL15.GL_STREAM_DRAW) {
            usage
        } else {
            GL15.GL_STREAM_DRAW
        }

        val uploadMode = RenderTuning.mergeScratchVboUploadMode
        if (uploadMode == RenderTuning.ScratchVboUploadMode.MAP_RANGE_UNSYNC && supportsMapBufferRange()) {
            GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)
            val access = GL30.GL_MAP_WRITE_BIT or GL30.GL_MAP_INVALIDATE_BUFFER_BIT or GL30.GL_MAP_UNSYNCHRONIZED_BIT
            val mapped = glMapBufferRangeCompat(OpenGlHelper.GL_ARRAY_BUFFER, 0L, totalBytes.toLong(), access)
            if (mapped != null) {
                for (s in segments) {
                    val len = s.lengthBytes
                    if (len <= 0) continue
                    val src = s.data.duplicate()
                    src.clear()
                    src.limit(len)
                    mapped.position(s.offsetBytes)
                    mapped.put(src)
                }
                val ok = GL15.glUnmapBuffer(OpenGlHelper.GL_ARRAY_BUFFER)
                if (!ok) {
                    GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)
                    for (s in segments) {
                        val len = s.lengthBytes
                        if (len <= 0) continue
                        val src = s.data.duplicate()
                        src.clear()
                        src.limit(len)
                        GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, s.offsetBytes.toLong(), src)
                    }
                }
            } else {
                GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)
                for (s in segments) {
                    val len = s.lengthBytes
                    if (len <= 0) continue
                    val src = s.data.duplicate()
                    src.clear()
                    src.limit(len)
                    GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, s.offsetBytes.toLong(), src)
                }
            }
        } else {
            // Allocate storage first (effectively orphaning). We then fill slices with sub-data.
            GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, totalBytes.toLong(), sanitizedUsage)

            for (s in segments) {
                val len = s.lengthBytes
                if (len <= 0) continue
                val src = s.data.duplicate()
                src.clear()
                src.limit(len)
                GL15.glBufferSubData(OpenGlHelper.GL_ARRAY_BUFFER, s.offsetBytes.toLong(), src)
            }
        }

        setupPointersForFormat(format)

        val repeats = RenderStress.drawMultiplier
        for (i in 0 until repeats) {
            GlStateManager.glDrawArrays(drawMode, 0, vertexCount)
        }

        teardownPointersForFormat(format)
        vbo.unbindBuffer()
    }

    private fun setupPointersForFormat(format: VertexFormat) {
        val stride = format.size
        val elements = format.elements

        for (i in elements.indices) {
            val e: VertexFormatElement = elements[i]
            val offset = format.getOffset(i).toLong()
            when (e.usage) {
                VertexFormatElement.EnumUsage.POSITION -> {
                    GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
                    GL11.glVertexPointer(e.elementCount, e.type.glConstant, stride, offset)
                }

                VertexFormatElement.EnumUsage.COLOR -> {
                    GL11.glEnableClientState(GL11.GL_COLOR_ARRAY)
                    GL11.glColorPointer(e.elementCount, e.type.glConstant, stride, offset)
                }

                VertexFormatElement.EnumUsage.UV -> {
                    // Map UV index -> texture unit. 0 = default, 1 = lightmap.
                    val unit = if (e.index == 0) OpenGlHelper.defaultTexUnit else OpenGlHelper.lightmapTexUnit
                    OpenGlHelper.setClientActiveTexture(unit)
                    GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
                    GL11.glTexCoordPointer(e.elementCount, e.type.glConstant, stride, offset)
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit)
                }

                VertexFormatElement.EnumUsage.NORMAL -> {
                    GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY)
                    GL11.glNormalPointer(e.type.glConstant, stride, offset)
                }

                else -> {
                    // PADDING / GENERIC: ignore.
                }
            }
        }
    }

    private fun teardownPointersForFormat(format: VertexFormat) {
        val elements = format.elements

        for (i in elements.indices) {
            val e: VertexFormatElement = elements[i]
            when (e.usage) {
                VertexFormatElement.EnumUsage.POSITION -> GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY)
                VertexFormatElement.EnumUsage.COLOR -> GL11.glDisableClientState(GL11.GL_COLOR_ARRAY)
                VertexFormatElement.EnumUsage.NORMAL -> GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY)
                VertexFormatElement.EnumUsage.UV -> {
                    val unit = if (e.index == 0) OpenGlHelper.defaultTexUnit else OpenGlHelper.lightmapTexUnit
                    OpenGlHelper.setClientActiveTexture(unit)
                    GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
                    OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit)
                }

                else -> {
                    // ignore
                }
            }
        }
    }
}
