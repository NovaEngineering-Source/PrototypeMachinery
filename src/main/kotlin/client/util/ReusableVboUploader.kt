package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import github.kasuminova.prototypemachinery.client.impl.render.RenderStress
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.WorldVertexBufferUploader
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.client.renderer.vertex.VertexFormatElement
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer

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

    private fun vboCacheEnabled(): Boolean = RenderTuning.vboCacheEnabled

    private var scratch: ByteBuffer? = null

    // Optional VBO path for merged draws.
    // We reuse a single VBO to avoid per-frame GL object churn.
    private var scratchVbo: VertexBuffer? = null
    private var scratchVboFormat: VertexFormat? = null

    /**
     * Explicitly release any GL/VBO resources held by this uploader.
     *
     * Call this on the main/render thread when the world is unloaded or resources are reloaded.
     */
    public fun dispose() {
        scratchVbo?.deleteGlBuffers()
        scratchVbo = null
        scratchVboFormat = null
        scratch = null
    }

    override fun draw(builder: BufferBuilder) {
        if (builder.vertexCount <= 0) return

        // Fast path: draw from a cached per-buffer VBO (no per-frame upload).
        if (vboCacheEnabled() && !mergeForceClientArrays() && OpenGlHelper.useVbo()) {
            val entry = BufferBuilderVboCache.getOrCreate(builder)
            if (entry != null) {
                entry.vbo.bindBuffer()
                setupPointersForFormat(entry.format)

                val repeats = RenderStress.drawMultiplier
                RenderStats.addDraw(repeats, entry.vertexCount * repeats)
                for (i in 0 until repeats) {
                    GlStateManager.glDrawArrays(entry.drawMode, 0, entry.vertexCount)
                }

                teardownPointersForFormat(entry.format)
                entry.vbo.unbindBuffer()
                return
            }
        }

        val buf = builder.byteBuffer

        val format = builder.vertexFormat
        val vertexSize = format.size
        // val buf = builder.byteBuffer // Already defined above
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

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            // Safety: do not merge incompatible formats.
            if (b.drawMode != drawMode || b.vertexFormat != format) {
                // Fallback: draw individually.
                builders.forEach { draw(it) }
                return
            }
            // IMPORTANT:
            // BufferBuilder.byteBuffer limit/capacity is not guaranteed to equal the amount of data written.
            // The only reliable size is vertexCount * vertexSize.
            totalBytes += b.vertexCount * vertexSize
            totalVertexCount += b.vertexCount
        }

        if (totalVertexCount <= 0) return

        // If per-buffer VBO caching is enabled and the bucket is large, prefer individual draws.
        // This avoids memcpy+merge and avoids uploading a giant merged VBO every frame.
        if (vboCacheEnabled() && !mergeForceClientArrays() && OpenGlHelper.useVbo()) {
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

        ensureScratch(totalBytes)
        val scratchBuf = scratch!!

        for (b in builders) {
            if (b.vertexCount <= 0) continue
            val bytesUsed = b.vertexCount * vertexSize
            // Duplicate to avoid mutating the original builder's position/limit (it may be reused).
            val src = b.byteBuffer.duplicate()
            src.clear()
            src.limit(bytesUsed)
            scratchBuf.put(src)
        }

        // Prepare for reading/uploading.
        scratchBuf.flip()

        // Fast path: when VBOs are enabled, upload the merged data once and draw from GPU memory.
        // This can reduce driver-side implicit copies and stalls vs client-side arrays.
        if (!mergeForceClientArrays() && OpenGlHelper.useVbo()) {
            // One batch draw, repeated N times.
            RenderStats.addDraw(RenderStress.drawMultiplier, totalVertexCount * RenderStress.drawMultiplier)
            drawMergedWithVbo(format, drawMode, totalVertexCount, scratchBuf)
            scratchBuf.clear()
            // Restore our expected limit for the next merge.
            scratchBuf.limit(totalBytes)
            return
        }

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
        // Ensure VBO instance matches the format.
        val vbo = if (scratchVbo == null || scratchVboFormat != format) {
            scratchVbo?.deleteGlBuffers()
            VertexBuffer(format).also {
                scratchVbo = it
                scratchVboFormat = format
            }
        } else {
            scratchVbo!!
        }

        // Upload to GPU.
        RenderStats.addVboUpload(data.remaining())
        vbo.bufferData(data)

        // Bind and set pointers with offsets (VBO path).
        vbo.bindBuffer()
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
