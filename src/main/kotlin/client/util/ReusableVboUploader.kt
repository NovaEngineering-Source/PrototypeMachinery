package github.kasuminova.prototypemachinery.client.util

import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GLAllocation
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.WorldVertexBufferUploader
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.client.renderer.vertex.VertexFormatElement
import java.nio.ByteBuffer

/**
 * A WorldVertexBufferUploader that reuses a single direct buffer for batched draws.
 *
 * Notes:
 * - This implementation assumes all merged builders share the same draw mode and vertex format.
 * - Primary purpose is to reduce draw call overhead; memcpy/merge cost may still be significant.
 */
public class ReusableVboUploader : WorldVertexBufferUploader() {

    private var scratch: ByteBuffer? = null

    override fun draw(builder: BufferBuilder) {
        if (builder.vertexCount <= 0) return

        val format = builder.vertexFormat
        val vertexSize = format.size
        val buf = builder.byteBuffer
        val elements = format.elements

        // Pre-draw setup
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            buf.position(format.getOffset(i))
            element.usage.preDraw(format, i, vertexSize, buf)
        }

        GlStateManager.glDrawArrays(builder.drawMode, 0, builder.vertexCount)

        // Post-draw cleanup
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

        var totalBytes = 0
        var totalVertexCount = 0

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            // Safety: do not merge incompatible formats.
            if (b.drawMode != drawMode || b.vertexFormat != format) {
                // Fallback: draw individually.
                builders.forEach { draw(it) }
                return
            }
            totalBytes += b.byteBuffer.limit()
            totalVertexCount += b.vertexCount
        }

        if (totalVertexCount <= 0) return

        ensureScratch(totalBytes)
        val scratchBuf = scratch!!

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            val src = b.byteBuffer
            src.rewind()
            scratchBuf.put(src)
        }

        val vertexSize = format.size
        val elements = format.elements

        // Pre-draw setup
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            scratchBuf.position(format.getOffset(i))
            element.usage.preDraw(format, i, vertexSize, scratchBuf)
        }

        GlStateManager.glDrawArrays(drawMode, 0, totalVertexCount)

        // Post-draw cleanup
        for (i in elements.indices) {
            val element: VertexFormatElement = elements[i]
            element.usage.postDraw(format, i, vertexSize, scratchBuf)
        }

        scratchBuf.clear()
    }

    private fun ensureScratch(minSize: Int) {
        val current = scratch
        if (current == null || current.capacity() < minSize) {
            scratch = GLAllocation.createDirectByteBuffer(minSize)
        }
        scratch!!.limit(minSize)
    }
}
