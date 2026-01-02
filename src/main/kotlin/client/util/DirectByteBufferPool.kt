package github.kasuminova.prototypemachinery.client.util

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLists
import java.lang.ref.WeakReference
import java.nio.ByteBuffer
import java.util.NavigableMap
import java.util.Queue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ConcurrentSkipListMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Tiny pool for direct [ByteBuffer] reuse.
 *
 * Why:
 * - Off-thread merge/packing can allocate many large direct buffers.
 * - Direct buffers are freed lazily; pooling avoids RSS spikes and GC/Cleaner stalls.
 */
internal object DirectByteBufferPool {

    internal data class StatsSnapshot(
        val enabled: Boolean,
        val borrowCalls: Long,
        val borrowHits: Long,
        val borrowMisses: Long,
        val recycleCalls: Long,
        val trimEvictions: Long,
        val pooledCount: Int,
        val pooledBytesHeld: Long,
        val createdCount: Int,
    )

    private val created: MutableList<WeakReference<ByteBuffer>> = ObjectLists.synchronize(ObjectArrayList())

    // capacity(bytes) -> available buffers
    private val pool: NavigableMap<Int, Queue<ByteBuffer>> = ConcurrentSkipListMap()

    // Best-effort accounting of bytes retained by idle pooled buffers.
    private var pooledBytes: Long = 0L

    private val borrowCalls = AtomicLong(0)
    private val borrowHits = AtomicLong(0)
    private val borrowMisses = AtomicLong(0)
    private val recycleCalls = AtomicLong(0)
    private val trimEvictions = AtomicLong(0)

    @Synchronized
    internal fun statsSnapshot(): StatsSnapshot {
        return StatsSnapshot(
            enabled = RenderTuning.directByteBufferPoolEnabled,
            borrowCalls = borrowCalls.get(),
            borrowHits = borrowHits.get(),
            borrowMisses = borrowMisses.get(),
            recycleCalls = recycleCalls.get(),
            trimEvictions = trimEvictions.get(),
            pooledCount = pooledCount(),
            pooledBytesHeld = pooledBytesHeld(),
            createdCount = createdCount(),
        )
    }

    @Synchronized
    fun borrow(minCapacityBytes: Int, tag: String? = null): ByteBuffer {
        borrowCalls.incrementAndGet()
        val enabled = RenderTuning.directByteBufferPoolEnabled
        if (!enabled) {
            borrowMisses.incrementAndGet()
            val b = NativeBuffers.createDirectByteBuffer(minCapacityBytes, tag = tag)
            created.add(WeakReference(b))
            return b
        }

        val entry = pool.ceilingEntry(minCapacityBytes)
        val q = entry?.value
        if (q != null) {
            val b = q.poll()
            if (q.isEmpty()) {
                pool.remove(entry.key)
            }
            if (b != null) {
                borrowHits.incrementAndGet()
                pooledBytes -= entry.key.toLong()
                try {
                    b.clear()
                } catch (_: Throwable) {
                    // ignore
                }
                return b
            }
        }

        borrowMisses.incrementAndGet()
        val b = NativeBuffers.createDirectByteBuffer(minCapacityBytes, tag = tag)
        created.add(WeakReference(b))
        return b
    }

    @Synchronized
    fun recycle(buf: ByteBuffer) {
        if (!RenderTuning.directByteBufferPoolEnabled) return

        recycleCalls.incrementAndGet()

        val cap = try {
            buf.capacity()
        } catch (_: Throwable) {
            return
        }

        try {
            buf.clear()
        } catch (_: Throwable) {
            // ignore
        }

        val q = pool.computeIfAbsent(cap) { ConcurrentLinkedQueue() }
        q.offer(buf)
        pooledBytes += cap.toLong()

        val maxEntries = RenderTuning.directByteBufferPoolMaxEntries
        val maxBytes = RenderTuning.directByteBufferPoolMaxBytes

        if ((maxEntries > 0 && pooledCount() > maxEntries) || (maxBytes > 0L && pooledBytes > maxBytes)) {
            trim(maxEntries, maxBytes)
        }
    }

    @Synchronized
    fun clearAll() {
        pool.clear()
        pooledBytes = 0L
    }

    @Synchronized
    fun pooledCount(): Int = pool.values.sumOf { it.size }

    @Synchronized
    fun pooledBytesHeld(): Long = pooledBytes

    @Synchronized
    fun createdCount(): Int = created.size

    private fun trim(maxEntries: Int, maxBytes: Long) {
        // Evict from the largest buffers first.
        while (true) {
            val tooMany = maxEntries > 0 && pooledCount() > maxEntries
            val tooBig = maxBytes > 0L && pooledBytes > maxBytes
            if (!tooMany && !tooBig) break

            val last = pool.lastEntry() ?: break
            val cap = last.key
            val q = last.value
            val evicted = q.poll()
            if (q.isEmpty()) {
                pool.remove(cap)
            }
            if (evicted != null) {
                trimEvictions.incrementAndGet()
                pooledBytes -= cap.toLong()
            } else {
                break
            }
        }
    }
}
