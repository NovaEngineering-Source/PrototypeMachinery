package github.kasuminova.prototypemachinery.client.impl.render.task

import java.util.concurrent.ForkJoinPool
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.ForkJoinWorkerThread
import java.util.concurrent.atomic.AtomicInteger

/**
 * Dedicated pool for CPU-side model buffer building.
 *
 * We avoid using the global common pool so that render/build workloads do not starve other async work.
 */
internal object RenderTaskExecutor {

    private val threadId = AtomicInteger(1)

    internal val pool: ForkJoinPool = ForkJoinPool(
        /* parallelism = */ maxOf(1, Runtime.getRuntime().availableProcessors() - 1),
        /* factory = */ ForkJoinPool.ForkJoinWorkerThreadFactory { p ->
            object : ForkJoinWorkerThread(p) {
                init {
                    name = "PM-RenderBuild-${threadId.getAndIncrement()}"
                    isDaemon = true
                }
            }
        },
        /* handler = */ null,
        /* asyncMode = */ true
    )

    internal fun <T : ForkJoinTask<*>> submit(task: T): T {
        pool.execute(task)
        return task
    }
}
