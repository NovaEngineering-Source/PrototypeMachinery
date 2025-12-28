package github.kasuminova.prototypemachinery.client.util

import it.unimi.dsi.fastutil.objects.ObjectArrayList
import it.unimi.dsi.fastutil.objects.ObjectLists
import net.minecraft.client.renderer.BufferBuilder
import java.lang.ref.WeakReference
import java.util.NavigableMap
import java.util.Queue
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

    /**
     * Borrow a builder whose byteBuffer capacity is >= [minCapacityBytes] if possible.
     *
     * The returned builder is reset and ready for begin().
     */
    @Synchronized
    fun borrow(minCapacityBytes: Int, tag: String? = null): BufferBuilder {
        NativeBufferStats.onBufferBuilderRequest(minCapacityBytes, tag)

        val entry = pool.ceilingEntry(minCapacityBytes)
        val q = entry?.value
        if (q != null) {
            val b = q.poll()
            if (q.isEmpty()) {
                pool.remove(entry.key)
            }
            if (b != null) {
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

        // Allocate new.
        NativeBufferStats.onBufferBuilderNew(minCapacityBytes, tag)
        val b = BufferBuilder(minCapacityBytes)
        created.add(WeakReference(b))
        return b
    }

    /** Return a builder back to the pool for reuse. */
    @Synchronized
    fun recycle(builder: BufferBuilder) {
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

        val q = pool.computeIfAbsent(cap) { ConcurrentLinkedQueue() }
        q.offer(builder)
    }

    fun pooledCount(): Int = pool.values.sumOf { it.size }

    fun createdCount(): Int = created.size

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
