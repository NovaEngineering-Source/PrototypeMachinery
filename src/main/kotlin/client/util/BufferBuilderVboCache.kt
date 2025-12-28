package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.client.renderer.vertex.VertexFormat
import java.nio.ByteBuffer
import java.util.WeakHashMap

/**
 * Caches a GPU VBO per [BufferBuilder] instance.
 *
 * Motivation:
 * - Our render pipeline often re-draws the *same* built [BufferBuilder] across many frames (cache hits).
 * - Merging buckets into a scratch buffer + uploading every frame can move tens of MiB per frame.
 * - By uploading each immutable built buffer once, subsequent frames can draw from GPU memory
 *   without re-uploading, dramatically reducing PCIe/driver bandwidth.
 *
 * Safety:
 * - A [BufferBuilder] is considered immutable once stored in a finished [BuiltBuffers].
 * - When the builder is recycled back to [BufferBuilderPool], we delete its VBO to avoid stale draws.
 *
 * All GL operations are scheduled onto the Minecraft render thread when needed.
 */
internal object BufferBuilderVboCache {

    internal data class Entry(
        val vbo: VertexBuffer,
        val format: VertexFormat,
        val drawMode: Int,
        val vertexCount: Int,
        val bytesUsed: Int,
    )

    private val map: MutableMap<BufferBuilder, Entry> = WeakHashMap()

    @Volatile
    private var bytesHeldEstimate: Long = 0L

    internal fun enabled(): Boolean = RenderTuning.vboCacheEnabled

    internal fun size(): Int = synchronized(this) { map.size }

    internal fun bytesHeld(): Long = bytesHeldEstimate

    /**
     * Called when a builder is returned to the pool.
     * Schedules VBO deletion and removes it from the cache.
     */
    internal fun onRecycle(builder: BufferBuilder) {
        if (!enabled()) {
            // If disabled, still try to drop any existing entry.
            removeAndDelete(builder)
            return
        }
        removeAndDelete(builder)
    }

    internal fun clearAll(reason: String) {
        val mc = Minecraft.getMinecraft()
        mc.addScheduledTask {
            val entries: List<Entry> = synchronized(this) {
                val list = map.values.toList()
                map.clear()
                bytesHeldEstimate = 0L
                list
            }
            for (e in entries) {
                runCatching { e.vbo.deleteGlBuffers() }
            }
        }
    }

    /**
     * Get or create (upload) a cached VBO for this builder.
     * Must be called on the render thread.
     */
    internal fun getOrCreate(builder: BufferBuilder): Entry? {
        if (!enabled()) return null
        if (!OpenGlHelper.useVbo()) return null
        if (builder.vertexCount <= 0) return null

        val format = builder.vertexFormat
        val drawMode = builder.drawMode
        val vertexCount = builder.vertexCount
        val bytesUsed = vertexCount * format.size
        if (bytesUsed <= 0) return null

        val existing = synchronized(this) { map[builder] }
        if (existing != null) {
            // If the builder was reused without recycle (should not happen), refresh.
            if (existing.format == format && existing.drawMode == drawMode && existing.vertexCount == vertexCount) {
                return existing
            }
            // Mismatch: drop and rebuild.
            removeAndDelete(builder)
        }

        // Upload.
        val vbo = VertexBuffer(format)

        val data: ByteBuffer = builder.byteBuffer.duplicate().also { dup ->
            dup.clear()
            dup.limit(bytesUsed)
        }

        RenderStats.addVboUpload(bytesUsed)
        vbo.bufferData(data)

        val entry = Entry(
            vbo = vbo,
            format = format,
            drawMode = drawMode,
            vertexCount = vertexCount,
            bytesUsed = bytesUsed,
        )

        synchronized(this) {
            map[builder] = entry
            bytesHeldEstimate += bytesUsed.toLong()
        }

        return entry
    }

    private fun removeAndDelete(builder: BufferBuilder) {
        val mc = Minecraft.getMinecraft()
        val entry = synchronized(this) {
            val e = map.remove(builder)
            if (e != null) {
                bytesHeldEstimate = (bytesHeldEstimate - e.bytesUsed.toLong()).coerceAtLeast(0L)
            }
            e
        } ?: return

        // Ensure GL deletion on render thread.
        if (mc.isCallingFromMinecraftThread) {
            runCatching { entry.vbo.deleteGlBuffers() }
        } else {
            mc.addScheduledTask {
                runCatching { entry.vbo.deleteGlBuffers() }
            }
        }
    }
}
