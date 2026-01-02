package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Dedicated pool for CPU-side model buffer building.
 *
 * We avoid using the global common pool so that render/build workloads do not starve other async work.
 */
internal object RenderTaskExecutor {

    private val threadId = AtomicInteger(1)

    private val coroutineThreadId = AtomicInteger(1)
    private val coroutineInFlight = AtomicLong(0)

    @Volatile
    private var coroutineScope: CoroutineScope? = null

    internal val pool: ForkJoinPool = ForkJoinPool(
        /* parallelism = */ maxOf(1, Runtime.getRuntime().availableProcessors() - 1),
        /* factory = */ { pool ->
            object : ForkJoinWorkerThread(pool) {
                init {
                    name = "PM-RenderBuild-${threadId.getAndIncrement()}"
                    isDaemon = true
                }
            }
        },
        /* handler = */ null,
        /* asyncMode = */ true
    )

    /**
     * Backlog hint used by animation auto-throttle.
     *
     * - Default: reads ForkJoinPool queued task count.
     * - Coroutine mode: uses a coarse in-flight count (queued + running) for render-build tasks.
     */
    internal fun queuedBuildTaskCount(): Long {
        return if (RenderTuning.renderBuildUseCoroutines) coroutineInFlight.get() else runCatching { pool.queuedTaskCount }.getOrDefault(0L)
    }

    private fun getOrCreateCoroutineScope(): CoroutineScope {
        val existing = coroutineScope
        if (existing != null) return existing

        // A dedicated fixed pool keeps scheduling predictable and avoids interacting with common pools.
        val parallelism = pool.parallelism
        val executor = Executors.newFixedThreadPool(parallelism) { r ->
            Thread(r).apply {
                name = "PM-RenderBuild-Coro-${coroutineThreadId.getAndIncrement()}"
                isDaemon = true
            }
        }
        val scope = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        coroutineScope = scope
        return scope
    }

    internal fun <T : ForkJoinTask<*>> submit(task: T): T {
        if (RenderTuning.renderBuildUseCoroutines) {
            // NOTE: We run the task via quietlyInvoke() so ForkJoinTask completion/exception state is still tracked.
            coroutineInFlight.incrementAndGet()
            getOrCreateCoroutineScope().launch {
                try {
                    task.quietlyInvoke()
                } finally {
                    coroutineInFlight.decrementAndGet()
                }
            }
        } else {
            // Default path: dedicated ForkJoinPool to avoid starving the common pool.
            pool.execute(task)
        }
        return task
    }
}
