package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLists
import net.minecraft.client.renderer.BufferBuilder
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.NavigableMap
import java.util.Queue
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListMap

/**
 * Thread-safe BufferBuilder pool to reduce off-heap (direct ByteBuffer) churn.
 *
 * Why:
 * - In MC 1.12, each BufferBuilder owns a direct ByteBuffer.
 * - Creating many BufferBuilders (even with small init sizes) can steadily increase RSS because
 *   direct buffers are freed lazily (Cleaner/GC).
 *
 * This pool reuses BufferBuilder instances by their current byteBuffer capacity.
 */
internal object BufferBuilderPool {

    private val created: MutableList<WeakReference<BufferBuilder>> = ObjectLists.synchronize(ObjectArrayList())

    // capacity(bytes) -> available builders
    private val pool: NavigableMap<Int, Queue<BufferBuilder>> = ConcurrentSkipListMap()

    private data class OversizeEntry(
        val capBytes: Int,
        val builder: BufferBuilder,
        var lastRecycleNanos: Long,
    )

    // Small LRU-like pool for very large builders (kept separate to avoid unbounded retention).
    // Size is intentionally tiny; lookup is linear.
    private val oversize: ArrayDeque<OversizeEntry> = ArrayDeque()

    // Best-effort accounting of how many bytes are currently retained by pooled builders.
    // (This excludes builders that are currently borrowed / in-use.)
    private var pooledBytes: Long = 0L
    private var oversizeBytes: Long = 0L

    private var oversizeDroppedCount: Long = 0L
    private var oversizeDroppedBytes: Long = 0L

    // Maintenance throttling: avoid doing cleanup/trim on every hot-path call.
    private var opsSinceMaintenance: Int = 0
    private var lastMaintenanceNanos: Long = 0L

    // --- Pinning (async-safe reads) ---
    // Some background tasks (e.g., async merge/packing) may need to read BufferBuilder.byteBuffer
    // after the render task cache decides to recycle old built buffers. To prevent data races where
    // a recycled builder gets reused and overwritten while still being read, we support pin/unpin.
    //
    // recycle(builder) will defer the actual pool return while pinned.
    private val pinCounts: MutableMap<BufferBuilder, Int> = Collections.synchronizedMap(WeakHashMap())
    private val pendingRecycle: MutableSet<BufferBuilder> = Collections.synchronizedSet(Collections.newSetFromMap(IdentityHashMap()))

    internal fun pin(builder: BufferBuilder) {
        synchronized(pinCounts) {
            val c = pinCounts[builder] ?: 0
            pinCounts[builder] = c + 1
        }
    }

    internal fun unpin(builder: BufferBuilder) {
        val shouldRecycle = synchronized(pinCounts) {
            val c = pinCounts[builder] ?: 0
            val newCount = if (c <= 1) {
                pinCounts.remove(builder)
                0
            } else {
                val nc = c - 1
                pinCounts[builder] = nc
                nc
            }

            val wasPending = pendingRecycle.remove(builder)
            newCount == 0 && wasPending
        }

        if (shouldRecycle) {
            // NOTE: we call recycle() again so it goes through the usual trimming logic.
            recycle(builder)
        }
    }

    private fun isPinned(builder: BufferBuilder): Boolean {
        return synchronized(pinCounts) { (pinCounts[builder] ?: 0) > 0 }
    }

    private fun bytesToInitInts(bytes: Int): Int {
        // MC 1.12 BufferBuilder(int) expects an INT count and allocates a direct ByteBuffer of (ints * 4) bytes.
        // Our APIs/tags are in bytes, so we must convert.
        if (bytes <= 0) return 0
        // ceil(bytes / 4)
        return (bytes + 3) ushr 2
    }

    private fun totalPooledBytes(): Long = pooledBytes + oversizeBytes

    private fun oversizeTtlNanos(): Long {
        val secs = RenderTuning.bufferBuilderPoolOversizeTtlSeconds
        if (secs <= 0) return 0L
        return secs.toLong() * 1_000_000_000L
    }

    private fun cleanupOversizeExpired(nowNanos: Long) {
        val ttl = oversizeTtlNanos()
        if (ttl <= 0L || oversize.isEmpty()) return

        // Evict from oldest to newest.
        while (true) {
            val first = oversize.firstOrNull() ?: break
            if (nowNanos - first.lastRecycleNanos <= ttl) break

            oversize.removeFirst()
            oversizeBytes -= first.capBytes.toLong()
        }
    }

    private fun trimToBudgets(nowNanos: Long) {
        if (!RenderTuning.bufferBuilderPoolTrimEnabled) return

        cleanupOversizeExpired(nowNanos)

        val maxTotal = RenderTuning.bufferBuilderPoolMaxTotalBytes
        if (maxTotal <= 0L) return

        while (totalPooledBytes() > maxTotal) {
            // Prefer evicting oversize first (it is expensive to keep around and rarely needed).
            val o = oversize.firstOrNull()
            if (o != null) {
                oversize.removeFirst()
                oversizeBytes -= o.capBytes.toLong()
                continue
            }

            // Then evict from the largest normal buckets.
            val last = pool.lastEntry() ?: break
            val evictCap = last.key
            val evictQ = last.value
            val evicted = evictQ.poll()
            if (evictQ.isEmpty()) {
                pool.remove(evictCap)
            }
            if (evicted != null) {
                pooledBytes -= evictCap.toLong()
            } else {
                break
            }
        }
    }

    private fun maybeMaintain(nowNanos: Long, force: Boolean) {
        if (!RenderTuning.bufferBuilderPoolTrimEnabled) return

        // Only do work periodically unless forced.
        // - ops threshold keeps overhead bounded even when nanoTime is cheap.
        // - time threshold prevents long pauses when ops are low.
        val ops = opsSinceMaintenance + 1
        opsSinceMaintenance = ops

        if (!force) {
            if (ops < 64) {
                // Only do timed maintenance when we already have a recent stamp.
                if (lastMaintenanceNanos != 0L && (nowNanos - lastMaintenanceNanos) < 1_000_000_000L) {
                    return
                }
            }
        }

        opsSinceMaintenance = 0
        lastMaintenanceNanos = nowNanos
        cleanupOversizeExpired(nowNanos)
        trimToBudgets(nowNanos)
    }

    /**
     * Borrow a builder whose byteBuffer capacity is >= [minCapacityBytes] if possible.
     *
     * The returned builder is reset and ready for begin().
     */
    @Synchronized
    fun borrow(minCapacityBytes: Int, tag: String? = null): BufferBuilder {
        NativeBufferStats.onBufferBuilderRequest(minCapacityBytes, tag)

        // Only pay for nanoTime when trimming/oversize logic is enabled.
        val doTrim = RenderTuning.bufferBuilderPoolTrimEnabled
        val doOversize = doTrim && RenderTuning.bufferBuilderPoolOversizeEnabled
        val nowNanos = if (doTrim || doOversize) System.nanoTime() else 0L
        if (doTrim && nowNanos != 0L) {
            // Opportunistic maintenance (not forced).
            maybeMaintain(nowNanos, force = false)
        }

        val entry = pool.ceilingEntry(minCapacityBytes)
        val q = entry?.value
        if (q != null) {
            val b = q.poll()
            if (q.isEmpty()) {
                pool.remove(entry.key)
            }
            if (b != null) {
                pooledBytes -= entry.key.toLong()
                // Ensure clean state
                try {
                    b.byteBuffer.clear()
                } catch (_: Throwable) {
                    // ignore
                }
                b.reset()
                return b
            }
        }

        // Try oversize pool (tiny linear scan): choose the smallest cap that still fits.
        if (doOversize && oversize.isNotEmpty()) {
            var bestIdx = -1
            var bestCap = Int.MAX_VALUE
            var idx = 0
            for (e in oversize) {
                val cap = e.capBytes
                if (cap >= minCapacityBytes && cap < bestCap) {
                    bestCap = cap
                    bestIdx = idx
                }
                idx++
            }

            if (bestIdx >= 0) {
                // Remove by index (ArrayDeque has no removeAt); rotate minimally.
                val tmp = ArrayList<OversizeEntry>(oversize.size)
                tmp.addAll(oversize)
                val chosen = tmp.removeAt(bestIdx)
                oversize.clear()
                oversize.addAll(tmp)

                oversizeBytes -= chosen.capBytes.toLong()

                val b = chosen.builder
                try {
                    b.byteBuffer.clear()
                } catch (_: Throwable) {
                    // ignore
                }
                b.reset()
                return b
            }
        }

        // Allocate new.
        NativeBufferStats.onBufferBuilderNew(minCapacityBytes, tag)
        val initInts = bytesToInitInts(minCapacityBytes)
        val b = BufferBuilder(initInts)
        created.add(WeakReference(b))
        return b
    }

    /** Return a builder back to the pool for reuse. */
    @Synchronized
    fun recycle(builder: BufferBuilder) {
        if (isPinned(builder)) {
            // Defer reuse until background readers are done.
            pendingRecycle.add(builder)
            return
        }

        // If the builder had a cached GPU VBO, it must be deleted before reuse to avoid stale draws.
        BufferBuilderVboCache.onRecycle(builder)
        try {
            builder.byteBuffer.clear()
        } catch (_: Throwable) {
            // ignore
        }
        builder.reset()

        val cap = try {
            builder.byteBuffer.capacity()
        } catch (_: Throwable) {
            return
        }

        val doTrim = RenderTuning.bufferBuilderPoolTrimEnabled
        val doOversize = doTrim && RenderTuning.bufferBuilderPoolOversizeEnabled
        val nowNanos = if (doTrim || doOversize) System.nanoTime() else 0L
        if (doTrim && nowNanos != 0L) {
            // Only force maintenance when we are likely to exceed budgets.
            // (This avoids forcing cleanup for small buffers.)
            val force = (RenderTuning.bufferBuilderPoolMaxTotalBytes > 0L) && (totalPooledBytes() > RenderTuning.bufferBuilderPoolMaxTotalBytes)
            maybeMaintain(nowNanos, force = force)
        }

        // Trimming: avoid pinning extremely large direct buffers forever.
        // But: keep a *tiny* oversize pool to avoid thrashing when a legitimately huge model is rendered repeatedly.
        if (doTrim) {
            val hardMax = RenderTuning.bufferBuilderPoolHardMaxKeepBytes
            if (hardMax > 0 && cap > hardMax) {
                oversizeDroppedCount++
                oversizeDroppedBytes += cap.toLong()
                return
            }

            val maxKeep = RenderTuning.bufferBuilderPoolMaxKeepBytes
            if (maxKeep > 0 && cap > maxKeep) {
                if (doOversize) {
                    val maxCount = RenderTuning.bufferBuilderPoolOversizeMaxCount
                    val maxBytes = RenderTuning.bufferBuilderPoolOversizeMaxTotalBytes

                    // If oversize budget is exhausted, drop oldest until it fits.
                    while (
                        (maxCount > 0 && oversize.size >= maxCount) ||
                        (maxBytes > 0L && (oversizeBytes + cap.toLong()) > maxBytes)
                    ) {
                        val ev = oversize.removeFirstOrNull() ?: break
                        oversizeBytes -= ev.capBytes.toLong()
                    }

                    // Add to oversize (acts like LRU: newest at end).
                    if (
                        (maxCount <= 0 || oversize.size < maxCount) &&
                        (maxBytes <= 0L || (oversizeBytes + cap.toLong()) <= maxBytes)
                    ) {
                        oversize.addLast(OversizeEntry(capBytes = cap, builder = builder, lastRecycleNanos = nowNanos))
                        oversizeBytes += cap.toLong()
                        if (nowNanos != 0L) {
                            // Only force if we actually exceed the total budget.
                            val force = (RenderTuning.bufferBuilderPoolMaxTotalBytes > 0L) && (totalPooledBytes() > RenderTuning.bufferBuilderPoolMaxTotalBytes)
                            maybeMaintain(nowNanos, force = force)
                        }
                        return
                    }
                }

                oversizeDroppedCount++
                oversizeDroppedBytes += cap.toLong()
                return
            }
        }

        val q = pool.computeIfAbsent(cap) { ConcurrentLinkedQueue() }
        q.offer(builder)
        pooledBytes += cap.toLong()

        if (doTrim && nowNanos != 0L) {
            // Only force maintenance if we exceed the configured total budget.
            val maxTotal = RenderTuning.bufferBuilderPoolMaxTotalBytes
            if (maxTotal > 0L && totalPooledBytes() > maxTotal) {
                maybeMaintain(nowNanos, force = true)
            }
        }
    }

    fun pooledCount(): Int = pool.values.sumOf { it.size }

    fun createdCount(): Int = created.size

    fun pooledBytesHeld(): Long = totalPooledBytes()

    fun oversizeCount(): Int = oversize.size

    fun oversizeBytesHeld(): Long = oversizeBytes

    fun oversizeDroppedCount(): Long = oversizeDroppedCount

    fun oversizeDroppedBytes(): Long = oversizeDroppedBytes

    fun estimatedBytesHeld(): Long {
        var sum = 0L
        val it = created.iterator()
        while (it.hasNext()) {
            val ref = it.next()
            val b = ref.get()
            if (b == null) {
                it.remove()
                continue
            }
            sum += runCatching { b.byteBuffer.capacity().toLong() }.getOrDefault(0L)
        }
        return sum
    }
}
