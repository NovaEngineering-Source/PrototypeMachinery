package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.vertex.VertexFormat
import java.nio.ByteBuffer
import java.util.ArrayDeque
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
        val vboId: Int,
        val format: VertexFormat,
        val drawMode: Int,
        val vertexCount: Int,
        val bytesUsed: Int,
    )

    private data class PooledVbo(
        val id: Int,
        val bytesEstimate: Int,
    )

    private val map: MutableMap<BufferBuilder, Entry> = WeakHashMap()

    // Reuse GL buffer objects to avoid expensive glGenBuffers churn on the render thread.
    private val pooled: ArrayDeque<PooledVbo> = ArrayDeque()

    @Volatile
    private var bytesHeldEstimate: Long = 0L

    @Volatile
    private var pooledBytesEstimate: Long = 0L

    internal fun enabled(): Boolean = RenderTuning.vboCacheEnabled

    internal fun size(): Int = synchronized(this) { map.size }

    internal fun bytesHeld(): Long = bytesHeldEstimate

    internal fun pooledBytesHeld(): Long = pooledBytesEstimate

    internal fun pooledCount(): Int = synchronized(this) { pooled.size }

    /**
     * Called when a builder is returned to the pool.
     * Schedules VBO deletion and removes it from the cache.
     */
    internal fun onRecycle(builder: BufferBuilder) {
        if (!enabled()) {
            // If disabled, still try to drop any existing entry.
            removeAndRelease(builder)
            return
        }
        removeAndRelease(builder)
    }

    internal fun clearAll(reason: String) {
        val mc = Minecraft.getMinecraft()
        mc.addScheduledTask {
            val toDelete: IntArray = synchronized(this) {
                val ids = IntArray(map.size + pooled.size)
                var i = 0
                for (e in map.values) {
                    ids[i++] = e.vboId
                }
                for (p in pooled) {
                    ids[i++] = p.id
                }
                map.clear()
                pooled.clear()
                bytesHeldEstimate = 0L
                pooledBytesEstimate = 0L
                ids
            }
            for (id in toDelete) {
                if (id != 0) {
                    runCatching { OpenGlHelper.glDeleteBuffers(id) }
                }
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
                RenderStats.addVboCacheHit()
                return existing
            }
            // Mismatch: drop and rebuild.
            removeAndRelease(builder)
        }

        // Upload.
        RenderStats.addVboCacheMiss()

        val vboId = acquireVboId(bytesUsed)
        if (vboId == 0) return null

        val data: ByteBuffer = builder.byteBuffer.duplicate().also { dup ->
            dup.clear()
            dup.limit(bytesUsed)
        }

        RenderStats.addVboUpload(bytesUsed)
        // Upload to ARRAY_BUFFER.
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, vboId)
        OpenGlHelper.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, data, OpenGlHelper.GL_STATIC_DRAW)
        OpenGlHelper.glBindBuffer(OpenGlHelper.GL_ARRAY_BUFFER, 0)

        val entry = Entry(
            vboId = vboId,
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

    private fun removeAndRelease(builder: BufferBuilder) {
        val mc = Minecraft.getMinecraft()
        val entry = synchronized(this) {
            val e = map.remove(builder)
            if (e != null) {
                bytesHeldEstimate = (bytesHeldEstimate - e.bytesUsed.toLong()).coerceAtLeast(0L)
            }
            e
        } ?: return

        // Ensure GL operations on render thread.
        val release = {
            releaseVboId(entry.vboId, entry.bytesUsed)
        }

        if (mc.isCallingFromMinecraftThread) {
            release()
        } else {
            mc.addScheduledTask { release() }
        }
    }

    private fun acquireVboId(bytesHint: Int): Int {
        if (!RenderTuning.vboCachePoolEnabled) {
            return runCatching { OpenGlHelper.glGenBuffers() }.getOrDefault(0)
        }

        val pooledVbo: PooledVbo? = synchronized(this) {
            val p = if (pooled.isEmpty()) null else pooled.removeFirst()
            if (p != null) {
                pooledBytesEstimate = (pooledBytesEstimate - p.bytesEstimate.toLong()).coerceAtLeast(0L)
            }
            p
        }

        if (pooledVbo != null) {
            return pooledVbo.id
        }

        return runCatching { OpenGlHelper.glGenBuffers() }.getOrDefault(0)
    }

    private fun releaseVboId(id: Int, bytesEstimate: Int) {
        if (id == 0) return

        if (!RenderTuning.vboCachePoolEnabled) {
            runCatching { OpenGlHelper.glDeleteBuffers(id) }
            return
        }

        val maxEntries = RenderTuning.vboCachePoolMaxEntries
        val maxBytes = RenderTuning.vboCachePoolMaxBytes

        // If budgets are exceeded, delete instead of pooling.
        synchronized(this) {
            val overEntries = maxEntries > 0 && pooled.size >= maxEntries
            val overBytes = maxBytes > 0 && (pooledBytesEstimate + bytesEstimate.toLong()) > maxBytes
            if (overEntries || overBytes) {
                // fallthrough; delete outside synchronized
            } else {
                pooled.addLast(PooledVbo(id = id, bytesEstimate = bytesEstimate.coerceAtLeast(0)))
                pooledBytesEstimate += bytesEstimate.toLong().coerceAtLeast(0L)
                return
            }
        }

        runCatching { OpenGlHelper.glDeleteBuffers(id) }
    }
}
