package github.kasuminova.prototypemachinery.impl.scheduler.backend

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.scheduler.SchedulingAffinity
import github.kasuminova.prototypemachinery.impl.platform.PMPlatformManager
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerMetrics
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerRuntimeSettings
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.Closeable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

internal class CoroutineTaskSchedulerBackend(
    settings: SchedulerRuntimeSettings,
) : TaskSchedulerBackend {

    override val backendName: String = "COROUTINES"

    private val platform = PMPlatformManager.get()

    @Volatile
    private var isShutdown = false

    @Volatile
    private var settings: SchedulerRuntimeSettings = settings

    private var executorService: ExecutorService = createExecutorService(settings)
    private var laneExecutors: Array<ExecutorService> = createLaneExecutors(settings)

    private var executorDispatcher: CloseableDispatcher = CloseableDispatcher(executorService.asCoroutineDispatcher())
    private var laneDispatchers: Array<CloseableDispatcher> = laneExecutors.map { CloseableDispatcher(it.asCoroutineDispatcher()) }.toTypedArray()

    private var scope: CoroutineScope = CoroutineScope(SupervisorJob())

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

        rebuildExecutors(settings)
        isShutdown = false
        PrototypeMachinery.logger.info("Task scheduler backend restarted ($backendName)")
    }

    @Synchronized
    private fun rebuildExecutors(settings: SchedulerRuntimeSettings) {
        // Best-effort shutdown old resources.
        runCatching { executorDispatcher.close() }
        for (d in laneDispatchers) {
            runCatching { d.close() }
        }
        runCatching { executorService.shutdownNow() }
        for (e in laneExecutors) {
            runCatching { e.shutdownNow() }
        }

        executorService = createExecutorService(settings)
        laneExecutors = createLaneExecutors(settings)
        executorDispatcher = CloseableDispatcher(executorService.asCoroutineDispatcher())
        laneDispatchers = laneExecutors.map { CloseableDispatcher(it.asCoroutineDispatcher()) }.toTypedArray()

        scope = CoroutineScope(SupervisorJob())
    }

    override fun reconfigure(settings: SchedulerRuntimeSettings) {
        this.settings = settings
        // The manager swaps backends on tick boundary; we don't hot-resize to avoid edge cases.
    }

    override fun shutdown() {
        if (isShutdown) return

        PrototypeMachinery.logger.info("Shutting down task scheduler backend ($backendName)...")
        isShutdown = true

        runCatching { (scope.coroutineContext[Job] as? Job)?.cancel() }

        runCatching { executorDispatcher.close() }
        for (d in laneDispatchers) {
            runCatching { d.close() }
        }

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

        val mainStart = System.nanoTime()
        val customMainCount = processCustomMainThreadTasks(state)
        val mainTasksCount = processMainThreadTasks(state)
        val mainPhaseMicros = (System.nanoTime() - mainStart) / 1000

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
        val jobs = ArrayList<Job>()

        var customConcurrentCount = 0
        while (true) {
            val task = state.customConcurrentTasks.poll() ?: break
            // Important: capture the task value, not the mutable loop variable.
            // Otherwise the coroutine may observe it as null after the loop advances.
            jobs.add(scope.launch(executorDispatcher.dispatcher) { safeRun(task) })
            customConcurrentCount++
        }

        val active = state.concurrentTasks.asSequence().filter { it.isActive() }.toList()
        if (active.isEmpty()) {
            val joinMicros = joinAll(jobs)
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
                    // Capture index per-iteration to avoid closure pitfalls.
                    val idx = i
                    jobs.add(scope.launch(executorDispatcher.dispatcher) { safeRun { active[idx].onSchedule() } })
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
            val laneDispatcher = laneDispatchers[laneIndex].dispatcher
            concurrentSchedulableCount += indexes.size
            jobs.add(
                scope.launch(laneDispatcher) {
                    for (i in indexes) {
                        safeRun { active[i].onSchedule() }
                    }
                }
            )
        }

        val joinMicros = joinAll(jobs)

        return ConcurrentStageResult(
            customConcurrentCount = customConcurrentCount,
            concurrentTasksCount = concurrentSchedulableCount,
            affinityGroups = groups.size,
            joinMicros = joinMicros,
        )
    }

    private fun joinAll(jobs: List<Job>): Long {
        val joinStart = System.nanoTime()
        runBlocking {
            try {
                jobs.joinAll()
            } catch (e: Throwable) {
                // joinAll itself normally doesn't throw, but keep parity with Java backend join barrier.
                PrototypeMachinery.logger.error("Error while joining coroutine jobs", e)
            }
        }
        return (System.nanoTime() - joinStart) / 1000
    }

    private fun laneIndexFor(keys: Set<Any>): Int {
        if (laneDispatchers.isEmpty()) return 0

        val parts = keys.asSequence().map { it.hashCode() }.sorted().toList()
        var h = 1
        for (p in parts) {
            h = 31 * h + p
        }
        return (h and Int.MAX_VALUE) % laneDispatchers.size
    }

    private fun safeRun(task: Runnable) {
        safeRun { task.run() }
    }

    private inline fun safeRun(block: () -> Unit) {
        runCatching { block() }
            .onFailure { e -> PrototypeMachinery.logger.error("Error executing concurrent task", e) }
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

    private class CloseableDispatcher(val dispatcher: CoroutineDispatcher) : Closeable {
        override fun close() {
            // ExecutorCoroutineDispatcher implements Closeable; but we only keep the CoroutineDispatcher type.
            // Best-effort close by reflection-less cast.
            (dispatcher as? Closeable)?.close()
        }
    }
}
