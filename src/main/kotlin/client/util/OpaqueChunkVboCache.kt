package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.util.ResourceLocation

/**
 * Experimental chunk-group VBO cache for opaque (RenderPass.DEFAULT) rendering.
 *
 * Keyed by (chunkGroupKey, texture, combinedLight). Each entry stores one merged VBO.
 *
 * Notes:
 * - Intended for DEFAULT (opaque) only. Transparent/bloom are excluded on purpose.
 * - Uses a fingerprint of the buffer list to detect content changes.
 * - Budget/limits are best-effort and tuned via [RenderTuning].
 */
internal class OpaqueChunkVboCache {

    internal data class Key(
        val chunkGroupKey: Long,
        val texture: ResourceLocation,
        val combinedLight: Int,
    )

    internal data class Entry(
        val vbo: VertexBuffer,
        var format: VertexFormat,
        var drawMode: Int,
        var vertexCount: Int,
        var bytesUsed: Int,
        var fingerprint: Long,
        var lastUsedFrame: Int,
    )

    // accessOrder=true => LinkedHashMap acts as LRU.
    private val map = LinkedHashMap<Key, Entry>(512, 0.75f, true)

    @Volatile
    private var bytesHeldEstimate: Long = 0L

    @Volatile
    private var frameCounter: Int = 0

    @Volatile
    private var evictions: Long = 0L

    @Volatile
    private var hits: Long = 0L

    @Volatile
    private var misses: Long = 0L

    internal fun size(): Int = map.size

    internal fun bytesHeld(): Long = bytesHeldEstimate

    internal fun statsSnapshot(): StatsSnapshot {
        return StatsSnapshot(
            size = size(),
            bytesHeld = bytesHeld(),
            hits = hits,
            misses = misses,
            evictions = evictions,
        )
    }

    internal data class StatsSnapshot(
        val size: Int,
        val bytesHeld: Long,
        val hits: Long,
        val misses: Long,
        val evictions: Long,
    )

    internal fun enabled(): Boolean {
        if (!RenderTuning.opaqueChunkVboCacheEnabled) return false
        if (RenderTuning.mergeForceClientArrays) return false
        if (!OpenGlHelper.useVbo()) return false
        return true
    }

    internal fun nextFrame() {
        // Overflow is fine; just a monotonic-ish hint.
        frameCounter++
    }

    /**
     * Get an existing entry if unchanged; otherwise rebuild/upload via the supplied [buildAndUpload] callback.
     *
     * Must be called on render thread.
     */
    internal inline fun getOrRebuildOrNull(
        key: Key,
        format: VertexFormat,
        drawMode: Int,
        bytesUsed: Int,
        vertexCount: Int,
        fingerprint: Long,
        buildAndUpload: (vbo: VertexBuffer) -> Boolean,
    ): Entry? {
        val frameId = frameCounter
        val existing = map[key]
        if (existing != null) {
            existing.lastUsedFrame = frameId
            if (existing.fingerprint == fingerprint && existing.format == format && existing.drawMode == drawMode && existing.vertexCount == vertexCount) {
                hits++
                RenderStats.addOpaqueChunkCacheHit()
                return existing
            }

            // Content changed: reuse VBO object but update metadata.
            misses++
            RenderStats.addOpaqueChunkCacheMiss()

            // Try rebuild. If it fails, keep the old entry untouched and let caller fallback.
            if (!buildAndUpload(existing.vbo)) {
                return null
            }

            bytesHeldEstimate = (bytesHeldEstimate - existing.bytesUsed.toLong()).coerceAtLeast(0L)

            existing.format = format
            existing.drawMode = drawMode
            existing.vertexCount = vertexCount
            existing.bytesUsed = bytesUsed
            existing.fingerprint = fingerprint
            existing.lastUsedFrame = frameId

            bytesHeldEstimate += bytesUsed.toLong()

            trimIfNeeded()
            return existing
        }

        // New entry.
        misses++
        RenderStats.addOpaqueChunkCacheMiss()

        val vbo = VertexBuffer(format)
        if (!buildAndUpload(vbo)) {
            runCatching { vbo.deleteGlBuffers() }
            return null
        }

        val e = Entry(
            vbo = vbo,
            format = format,
            drawMode = drawMode,
            vertexCount = vertexCount,
            bytesUsed = bytesUsed,
            fingerprint = fingerprint,
            lastUsedFrame = frameId,
        )

        map[key] = e
        bytesHeldEstimate += bytesUsed.toLong()

        trimIfNeeded()
        return e
    }

    internal fun clearAll(reason: String) {
        if (map.isEmpty()) return

        val mc = Minecraft.getMinecraft()
        val entries = map.values.toList()
        map.clear()
        bytesHeldEstimate = 0L

        val delete = {
            for (e in entries) {
                runCatching { e.vbo.deleteGlBuffers() }
            }
        }

        if (mc.isCallingFromMinecraftThread) {
            delete()
        } else {
            mc.addScheduledTask { delete() }
        }
    }

    private fun trimIfNeeded() {
        val maxEntries = RenderTuning.opaqueChunkVboCacheMaxEntries
        val maxBytes = RenderTuning.opaqueChunkVboCacheMaxBytes

        // Fast path: nothing to do.
        val overEntries = maxEntries > 0 && map.size > maxEntries
        val overBytes = maxBytes > 0 && bytesHeldEstimate > maxBytes
        if (!overEntries && !overBytes) return

        val it = map.entries.iterator()
        while (it.hasNext() && ((maxEntries > 0 && map.size > maxEntries) || (maxBytes > 0 && bytesHeldEstimate > maxBytes))) {
            val eldest = it.next().value
            it.remove()
            bytesHeldEstimate = (bytesHeldEstimate - eldest.bytesUsed.toLong()).coerceAtLeast(0L)
            evictions++
            RenderStats.addOpaqueChunkCacheEviction()
            runCatching { eldest.vbo.deleteGlBuffers() }
        }
    }
}
