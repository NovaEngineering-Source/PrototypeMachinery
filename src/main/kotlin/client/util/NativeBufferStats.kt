package github.kasuminova.prototypemachinery.client.util

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight counters for native/direct buffer allocation originating from PM code.
 *
 * Notes:
 * - This does NOT capture allocations performed inside Minecraft/Forge/GeckoLib/LWJGL internals unless
 *   our code path calls into them via the helpers in [NativeBuffers].
 * - Intended for correlating "system RSS keeps growing" with suspicious allocation hot paths.
 */
internal object NativeBufferStats {

    internal data class TagSnapshot(
        val tag: String,
        val allocations: Long,
        val bytes: Long,
    )

    internal data class Snapshot(
        val directAllocations: Long,
        val directAllocatedBytes: Long,
        val bufferBuilderRequests: Long,
        val bufferBuilderRequestBytes: Long,
        val bufferBuilderNew: Long,
        val bufferBuilderNewBytes: Long,
        val topDirectTags: List<TagSnapshot>,
        val topBufferBuilderRequestTags: List<TagSnapshot>,
        val topBufferBuilderNewTags: List<TagSnapshot>,
    )

    private val directAllocations = AtomicLong(0)
    private val directAllocatedBytes = AtomicLong(0)

    private val bufferBuilderRequests = AtomicLong(0)
    private val bufferBuilderRequestBytes = AtomicLong(0)

    private val bufferBuilderNew = AtomicLong(0)
    private val bufferBuilderNewBytes = AtomicLong(0)

    private val directAllocationsByTag = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()
    private val directBytesByTag = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()

    private val bufferBuilderRequestsByTag = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()
    private val bufferBuilderRequestBytesByTag = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()

    private val bufferBuilderNewByTag = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()
    private val bufferBuilderNewBytesByTag = java.util.concurrent.ConcurrentHashMap<String, AtomicLong>()

    internal fun onDirectAlloc(bytes: Int, tag: String?) {
        directAllocations.incrementAndGet()
        if (bytes > 0) directAllocatedBytes.addAndGet(bytes.toLong())

        val k = tag ?: "<unknown>"
        directAllocationsByTag.computeIfAbsent(k) { AtomicLong(0) }.incrementAndGet()
        if (bytes > 0) {
            directBytesByTag.computeIfAbsent(k) { AtomicLong(0) }.addAndGet(bytes.toLong())
        }
    }

    internal fun onBufferBuilderRequest(initBytes: Int, tag: String?) {
        bufferBuilderRequests.incrementAndGet()
        if (initBytes > 0) bufferBuilderRequestBytes.addAndGet(initBytes.toLong())

        val k = tag ?: "<unknown>"
        bufferBuilderRequestsByTag.computeIfAbsent(k) { AtomicLong(0) }.incrementAndGet()
        if (initBytes > 0) {
            bufferBuilderRequestBytesByTag.computeIfAbsent(k) { AtomicLong(0) }.addAndGet(initBytes.toLong())
        }
    }

    internal fun onBufferBuilderNew(initBytes: Int, tag: String?) {
        bufferBuilderNew.incrementAndGet()
        if (initBytes > 0) bufferBuilderNewBytes.addAndGet(initBytes.toLong())

        val k = tag ?: "<unknown>"
        bufferBuilderNewByTag.computeIfAbsent(k) { AtomicLong(0) }.incrementAndGet()
        if (initBytes > 0) {
            bufferBuilderNewBytesByTag.computeIfAbsent(k) { AtomicLong(0) }.addAndGet(initBytes.toLong())
        }
    }

    internal fun snapshot(): Snapshot {
        val topDirect = topTags(directAllocationsByTag, directBytesByTag, limit = 3)
        val topBuilderReq = topTags(bufferBuilderRequestsByTag, bufferBuilderRequestBytesByTag, limit = 5)
        val topBuilderNew = topTags(bufferBuilderNewByTag, bufferBuilderNewBytesByTag, limit = 5)
        return Snapshot(
            directAllocations = directAllocations.get(),
            directAllocatedBytes = directAllocatedBytes.get(),
            bufferBuilderRequests = bufferBuilderRequests.get(),
            bufferBuilderRequestBytes = bufferBuilderRequestBytes.get(),
            bufferBuilderNew = bufferBuilderNew.get(),
            bufferBuilderNewBytes = bufferBuilderNewBytes.get(),
            topDirectTags = topDirect,
            topBufferBuilderRequestTags = topBuilderReq,
            topBufferBuilderNewTags = topBuilderNew,
        )
    }

    private fun topTags(
        allocs: java.util.concurrent.ConcurrentHashMap<String, AtomicLong>,
        bytes: java.util.concurrent.ConcurrentHashMap<String, AtomicLong>,
        limit: Int,
    ): List<TagSnapshot> {
        if (allocs.isEmpty()) return emptyList()

        val out = ArrayList<TagSnapshot>(minOf(limit, allocs.size))
        for ((tag, a) in allocs.entries) {
            val b = bytes[tag]?.get() ?: 0L
            out.add(TagSnapshot(tag = tag, allocations = a.get(), bytes = b))
        }

        out.sortWith(compareByDescending<TagSnapshot> { it.bytes }.thenByDescending { it.allocations })
        if (out.size > limit) {
            return out.subList(0, limit)
        }
        return out
    }
}
