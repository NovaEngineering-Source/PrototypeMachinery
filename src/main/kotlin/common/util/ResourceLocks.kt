package github.kasuminova.prototypemachinery.common.util

import com.google.common.util.concurrent.Striped
import github.kasuminova.prototypemachinery.api.scheduler.AffinityKey
import java.util.concurrent.locks.Lock

/**
 * Global striped locks for synchronizing access to shared world-backed resources.
 *
 * Notes:
 * - These locks only protect code paths that *also* use this utility.
 * - They do NOT automatically protect vanilla / other-mod accesses.
 *
 * 用于对“世界中的共享资源（例如某个 BlockPos 的库存/流体/能量端口）”进行同步的全局条带锁。
 *
 * 注意：
 * - 该锁只对同样使用本工具的代码路径生效。
 * - 无法自动约束原版/其他模组对同一容器的访问。
 */
public object ResourceLocks {

    // 128 stripes is usually a good balance between contention and overhead.
    private val striped: Striped<Lock> = Striped.lock(128)

    public fun lockOf(key: AffinityKey): Lock = striped.get(key)

    public inline fun <T> withLock(key: AffinityKey, action: () -> T): T {
        val lock = lockOf(key)
        lock.lock()
        return try {
            action()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Acquire multiple locks in a deterministic order to avoid deadlocks.
     *
     * 按确定性顺序获取多个锁，避免死锁。
     */
    public inline fun <T> withLocks(keys: Iterable<AffinityKey>, action: () -> T): T {
        val ordered = keys
            .distinct()
            .sorted()
            .toList()

        val acquired = ArrayList<Lock>(ordered.size)
        try {
            for (k in ordered) {
                val lock = lockOf(k)
                lock.lock()
                acquired.add(lock)
            }
            return action()
        } finally {
            for (i in acquired.size - 1 downTo 0) {
                acquired[i].unlock()
            }
        }
    }
}
