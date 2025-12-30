package github.kasuminova.prototypemachinery.impl.scheduler.backend

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.scheduler.SchedulingAffinity
import github.kasuminova.prototypemachinery.impl.platform.PMPlatformManager
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerMetrics
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerRuntimeSettings
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal class JavaTaskSchedulerBackend(
    settings: SchedulerRuntimeSettings,
) : TaskSchedulerBackend {

    override val backendName: String = "JAVA"

    private val platform = PMPlatformManager.get()

    @Volatile
    private var isShutdown = false

    @Volatile
    private var settings: SchedulerRuntimeSettings = settings

    private var executorService: ExecutorService = createExecutorService(settings)
    private var laneExecutors: Array<ExecutorService> = createLaneExecutors(settings)

    init {
        PrototypeMachinery.logger.info(
            "Task scheduler backend '{}' initialized on platform '{}' (executor={}, lanes={})",
            backendName,
            platform.id(),
            executorService.javaClass.name,
            laneExecutors.size
        )
    }

    private fun createExecutorService(settings: SchedulerRuntimeSettings): ExecutorService =
        platform.createSchedulerExecutor(settings.workerThreads.coerceAtLeast(1), "PrototypeMachinery-Scheduler")

    private fun createLaneExecutors(settings: SchedulerRuntimeSettings): Array<ExecutorService> {
        val lanes = settings.laneCount.coerceAtLeast(1)
        return Array(lanes) { lane ->
            platform.createSchedulerLaneExecutor(lane + 1, "PrototypeMachinery-Scheduler")
        }
    }

    @Synchronized
    private fun restartIfNeeded() {
        if (!isShutdown) return

        executorService = createExecutorService(settings)
        laneExecutors = createLaneExecutors(settings)
        isShutdown = false
        PrototypeMachinery.logger.info("Task scheduler backend restarted ($backendName)")
    }

    override fun reconfigure(settings: SchedulerRuntimeSettings) {
        this.settings = settings
        // Recreate executors at next restart boundary.
        // 若希望立即生效，这里可以做一次热重启；但为了安全，交由上层在 tick 边界切换 backend。
    }

    override fun shutdown() {
        if (isShutdown) return

        PrototypeMachinery.logger.info("Shutting down task scheduler backend ($backendName)...")
        isShutdown = true

        executorService.shutdown()
        for (lane in laneExecutors) {
            lane.shutdown()
        }

        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                PrototypeMachinery.logger.warn("Executor service did not terminate in time, forcing shutdown")
                executorService.shutdownNow()
            }
        } catch (e: InterruptedException) {
            PrototypeMachinery.logger.error("Interrupted while waiting for executor service shutdown", e)
            executorService.shutdownNow()
            Thread.currentThread().interrupt()
        }

        for (lane in laneExecutors) {
            try {
                if (!lane.awaitTermination(10, TimeUnit.SECONDS)) {
                    PrototypeMachinery.logger.warn("Lane executor did not terminate in time, forcing shutdown")
                    lane.shutdownNow()
                }
            } catch (e: InterruptedException) {
                PrototypeMachinery.logger.error("Interrupted while waiting for lane executor shutdown", e)
                lane.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }

        PrototypeMachinery.logger.info("Task scheduler backend shutdown complete ($backendName)")
    }

    override fun onServerTickEnd(state: SchedulerState, metrics: SchedulerMetrics) {
        if (isShutdown) return

        metrics.beginTick(backendName)

        val tickStart = System.nanoTime()

        // 1) main thread custom tasks
        val mainStart = System.nanoTime()
        val customMainCount = processCustomMainThreadTasks(state)

        // 2) main thread schedulables
        val mainTasksCount = processMainThreadTasks(state)
        val mainPhaseMicros = (System.nanoTime() - mainStart) / 1000

        // 3) concurrent stage + join barrier
        val concurrentStart = System.nanoTime()
        val concurrentResult = submitConcurrentTasksAndJoin(state)
        val concurrentPhaseMicros = (System.nanoTime() - concurrentStart) / 1000

        val totalTickMicros = (System.nanoTime() - tickStart) / 1000

        metrics.record(
            mainPhaseMicros = mainPhaseMicros,
            concurrentPhaseMicros = concurrentPhaseMicros,
            joinMicros = concurrentResult.joinMicros,
            totalTickMicros = totalTickMicros,
            mainTasks = mainTasksCount,
            concurrentTasks = concurrentResult.concurrentTasksCount,
            customMainTasks = customMainCount,
            customConcurrentTasks = concurrentResult.customConcurrentCount,
            affinityGroups = concurrentResult.affinityGroups,
        )
    }

    private fun processCustomMainThreadTasks(state: SchedulerState): Int {
        var executed = 0
        var task = state.customMainThreadTasks.poll()
        while (task != null) {
            try {
                task.run()
            } catch (e: Throwable) {
                PrototypeMachinery.logger.error("Error executing custom main thread task", e)
            }
            executed++
            task = state.customMainThreadTasks.poll()
        }
        return executed
    }

    private fun processMainThreadTasks(state: SchedulerState): Int {
        var executed = 0
        for (schedulable in state.mainThreadTasks) {
            if (!schedulable.isActive()) continue
            try {
                schedulable.onSchedule()
            } catch (e: Throwable) {
                PrototypeMachinery.logger.error("Error executing main thread schedulable: ${schedulable.javaClass.simpleName}", e)
            }
            executed++
        }
        return executed
    }

    private data class ConcurrentStageResult(
        val customConcurrentCount: Int,
        val concurrentTasksCount: Int,
        val affinityGroups: Int,
        val joinMicros: Long,
    )

    private fun submitConcurrentTasksAndJoin(state: SchedulerState): ConcurrentStageResult {
        val futures = ArrayList<Future<*>>()

        var customConcurrentCount = 0
        var customTask = state.customConcurrentTasks.poll()
        while (customTask != null) {
            futures.add(executorService.submit(SafeRunnable(customTask)))
            customConcurrentCount++
            customTask = state.customConcurrentTasks.poll()
        }

        val active = state.concurrentTasks.asSequence().filter { it.isActive() }.toList()
        if (active.isEmpty()) {
            val joinMicros = joinAll(futures)
            return ConcurrentStageResult(
                customConcurrentCount = customConcurrentCount,
                concurrentTasksCount = 0,
                affinityGroups = 0,
                joinMicros = joinMicros,
            )
        }

        val uf = UnionFind(active.size)
        val keyToIndex = HashMap<Any, Int>(active.size * 2)
        val affinityKeysByIndex: Array<Set<Any>> = Array(active.size) { emptySet() }

        for (i in active.indices) {
            val keys = (active[i] as? SchedulingAffinity)?.getSchedulingAffinityKeys().orEmpty()
            affinityKeysByIndex[i] = keys

            for (k in keys) {
                val prev = keyToIndex.putIfAbsent(k, i)
                if (prev != null) {
                    uf.union(i, prev)
                }
            }
        }

        val groups = HashMap<Int, MutableList<Int>>()
        for (i in active.indices) {
            val root = uf.find(i)
            groups.computeIfAbsent(root) { ArrayList() }.add(i)
        }

        var concurrentSchedulableCount = 0
        for ((_, indexes) in groups) {
            val hasAffinity = indexes.any { affinityKeysByIndex[it].isNotEmpty() }
            if (!hasAffinity) {
                for (i in indexes) {
                    futures.add(executorService.submit(SafeRunnable { active[i].onSchedule() }))
                    concurrentSchedulableCount++
                }
                continue
            }

            val mergedKeys = LinkedHashSet<Any>()
            for (i in indexes) {
                mergedKeys.addAll(affinityKeysByIndex[i])
            }

            indexes.sortBy { System.identityHashCode(active[it]) }

            val laneIndex = laneIndexFor(mergedKeys)
            val lane = laneExecutors[laneIndex]
            concurrentSchedulableCount += indexes.size
            futures.add(
                lane.submit(
                    SafeRunnable {
                        for (i in indexes) {
                            active[i].onSchedule()
                        }
                    }
                )
            )
        }

        val joinMicros = joinAll(futures)

        return ConcurrentStageResult(
            customConcurrentCount = customConcurrentCount,
            concurrentTasksCount = concurrentSchedulableCount,
            affinityGroups = groups.size,
            joinMicros = joinMicros,
        )
    }

    private fun joinAll(futures: List<Future<*>>): Long {
        val joinStart = System.nanoTime()
        for (f in futures) {
            try {
                f.get()
            } catch (e: Throwable) {
                PrototypeMachinery.logger.error("Error while joining concurrent tasks", e)
            }
        }
        return (System.nanoTime() - joinStart) / 1000
    }

    private fun laneIndexFor(keys: Set<Any>): Int {
        if (laneExecutors.isEmpty()) return 0

        val parts = keys.asSequence().map { it.hashCode() }.sorted().toList()
        var h = 1
        for (p in parts) {
            h = 31 * h + p
        }
        return (h and Int.MAX_VALUE) % laneExecutors.size
    }

    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }
        private val rank = IntArray(size)

        fun find(x: Int): Int {
            var v = x
            while (parent[v] != v) {
                parent[v] = parent[parent[v]]
                v = parent[v]
            }
            return v
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra == rb) return

            val rka = rank[ra]
            val rkb = rank[rb]
            when {
                rka < rkb -> parent[ra] = rb
                rka > rkb -> parent[rb] = ra
                else -> {
                    parent[rb] = ra
                    rank[ra] = rka + 1
                }
            }
        }
    }

    private class SafeRunnable(private val task: Runnable) : Runnable {
        override fun run() {
            runCatching { task.run() }
                .onFailure { e -> PrototypeMachinery.logger.error("Error executing concurrent task", e) }
        }
    }
}
