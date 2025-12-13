package github.kasuminova.prototypemachinery.impl.scheduler

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode
import github.kasuminova.prototypemachinery.api.scheduler.ISchedulable
import github.kasuminova.prototypemachinery.api.scheduler.SchedulingAffinity
import github.kasuminova.prototypemachinery.api.scheduler.TaskScheduler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implementation of TaskScheduler.
 * TaskScheduler 的实现。
 *
 * Executes schedulable tasks concurrently using a thread pool,
 * while respecting execution mode preferences.
 *
 * 使用线程池并发执行可调度任务，
 * 同时尊重执行模式偏好。
 */
internal object TaskSchedulerImpl : TaskScheduler {

    private val mainThreadTasks = ConcurrentHashMap.newKeySet<ISchedulable>()
    private val concurrentTasks = ConcurrentHashMap.newKeySet<ISchedulable>()

    private val customMainThreadTasks = ConcurrentLinkedQueue<Runnable>()
    private val customConcurrentTasks = ConcurrentLinkedQueue<Runnable>()

    private val threadCounter = AtomicInteger(0)

    @Volatile
    private var isShutdown = false

    // Thread pool for concurrent execution
    // 并发执行的线程池
    private var executorService: ExecutorService = createExecutorService()

    // Single-thread lanes for affinity grouped tasks.
    // 用于 affinity 分组任务的单线程 lanes。
    private var laneExecutors: Array<ExecutorService> = createLaneExecutors()

    private fun createExecutorService(): ExecutorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    ) { runnable ->
        Thread(runnable, "PrototypeMachinery-Scheduler-${threadCounter.incrementAndGet()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
        }
    }

    private fun createLaneExecutors(): Array<ExecutorService> {
        val lanes = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)
        return Array(lanes) { lane ->
            Executors.newSingleThreadExecutor { runnable ->
                Thread(runnable, "PrototypeMachinery-Scheduler-Lane-${lane + 1}").apply {
                    isDaemon = true
                    priority = Thread.NORM_PRIORITY + 1
                }
            }
        }
    }

    /**
     * Restart the scheduler if it has been shut down.
     * 如果调度器已关闭，则重新启动。
     *
     * This is needed for single-player where the server can be stopped and restarted.
     * 这对于单机游戏很必要，因为服务器可以停止并重新启动。
     */
    @Synchronized
    private fun restartIfNeeded() {
        if (isShutdown) {
            executorService = createExecutorService()
            laneExecutors = createLaneExecutors()
            isShutdown = false
            PrototypeMachinery.logger.info("Task scheduler restarted")
        }
    }

    override fun register(schedulable: ISchedulable) {
        restartIfNeeded()
        when (schedulable.getExecutionMode()) {
            ExecutionMode.MAIN_THREAD -> mainThreadTasks.add(schedulable)
            ExecutionMode.CONCURRENT -> concurrentTasks.add(schedulable)
        }
        PrototypeMachinery.logger.debug("Registered schedulable: {} ({})", schedulable.javaClass.simpleName, schedulable.getExecutionMode())
    }

    override fun unregister(schedulable: ISchedulable) {
        val removed = mainThreadTasks.remove(schedulable) || concurrentTasks.remove(schedulable)
        if (removed) {
            PrototypeMachinery.logger.debug("Unregistered schedulable: ${schedulable.javaClass.simpleName}")
        }
    }

    override fun submitTask(task: Runnable, executionMode: ExecutionMode) {
        restartIfNeeded()
        when (executionMode) {
            ExecutionMode.MAIN_THREAD -> customMainThreadTasks.offer(task)
            ExecutionMode.CONCURRENT -> customConcurrentTasks.offer(task)
        }
    }

    override fun getRegisteredCount(): Int = mainThreadTasks.size + concurrentTasks.size

    /**
     * Shutdown the scheduler and cleanup resources.
     * 关闭调度器并清理资源。
     *
     * This should be called when the server is stopping.
     * 应在服务器停止时调用。
     */
    @Synchronized
    internal fun shutdown() {
        if (isShutdown) {
            return
        }

        PrototypeMachinery.logger.info("Shutting down task scheduler...")

        isShutdown = true

        // Stop accepting new tasks first.
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

        mainThreadTasks.clear()
        concurrentTasks.clear()
        customMainThreadTasks.clear()
        customConcurrentTasks.clear()

        PrototypeMachinery.logger.info("Task scheduler shutdown complete")
    }

    /**
     * Handle server tick event.
     * 处理服务器 Tick 事件。
     */
    @SubscribeEvent
    internal fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END || isShutdown) {
            return
        }

        // Execute custom main thread tasks
        // 执行自定义主线程任务
        processCustomMainThreadTasks()

        // Execute main thread tasks
        // 执行主线程可调度任务
        processMainThreadTasks()

        // Submit concurrent tasks to thread pool
        // 提交并发任务到线程池
        submitConcurrentTasksAndJoin()
    }

    /**
     * Process custom main thread tasks.
     * 处理自定义主线程任务。
     */
    private fun processCustomMainThreadTasks() {
        var task = customMainThreadTasks.poll()
        while (task != null) {
            try {
                task.run()
            } catch (e: Throwable) {
                PrototypeMachinery.logger.error("Error executing custom main thread task", e)
            }
            task = customMainThreadTasks.poll()
        }
    }

    /**
     * Process main thread tasks.
     * 处理主线程可调度任务。
     */
    private fun processMainThreadTasks() {
        for (schedulable in mainThreadTasks) {
            if (!schedulable.isActive()) {
                continue
            }

            try {
                schedulable.onSchedule()
            } catch (e: Throwable) {
                PrototypeMachinery.logger.error("Error executing main thread schedulable: ${schedulable.javaClass.simpleName}", e)
            }
        }
    }

    /**
     * Submit concurrent tasks to thread pool.
     * 提交并发任务到线程池。
     */
    private fun submitConcurrentTasksAndJoin() {
        val futures = ArrayList<Future<*>>()

        // Submit custom concurrent tasks (no affinity)
        // 提交自定义并发任务（无 affinity）
        var customTask = customConcurrentTasks.poll()
        while (customTask != null) {
            futures.add(executorService.submit(SafeRunnable(customTask)))
            customTask = customConcurrentTasks.poll()
        }

        // Snapshot active schedulables
        // 快照活跃的可调度任务
        val active = concurrentTasks.asSequence().filter { it.isActive() }.toList()
        if (active.isEmpty()) {
            // Still need to join custom concurrent tasks.
            for (f in futures) {
                try {
                    f.get()
                } catch (e: Throwable) {
                    PrototypeMachinery.logger.error("Error while joining concurrent tasks", e)
                }
            }
            return
        }

        // Union-find grouping by shared affinity keys (A 方案：共享 IO 设备)
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

        for ((_, indexes) in groups) {
            // No affinity keys -> keep parallel behavior
            val hasAffinity = indexes.any { affinityKeysByIndex[it].isNotEmpty() }
            if (!hasAffinity) {
                for (i in indexes) {
                    futures.add(executorService.submit(SafeRunnable { active[i].onSchedule() }))
                }
                continue
            }

            // Merge keys for lane selection
            val mergedKeys = LinkedHashSet<Any>()
            for (i in indexes) {
                mergedKeys.addAll(affinityKeysByIndex[i])
            }

            // Deterministic order within group reduces jitter
            indexes.sortBy { System.identityHashCode(active[it]) }

            val laneIndex = laneIndexFor(mergedKeys)
            val lane = laneExecutors[laneIndex]
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

        // Join barrier: block main thread until all concurrent work for this tick is finished.
        // Join 屏障：阻塞主线程直到本 tick 所有并发任务完成。
        for (f in futures) {
            try {
                f.get()
            } catch (e: Throwable) {
                PrototypeMachinery.logger.error("Error while joining concurrent tasks", e)
            }
        }
    }

    private fun laneIndexFor(keys: Set<Any>): Int {
        if (laneExecutors.isEmpty()) return 0

        // Stable-ish hash: sort component hashes to avoid iteration-order noise.
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

    /**
     * Wrapper for safe task execution with error handling.
     * 用于安全任务执行的包装器，带错误处理。
     */
    private class SafeRunnable(private val task: Runnable) : Runnable {
        override fun run() {
            runCatching { task.run() }.onFailure { e -> PrototypeMachinery.logger.error("Error executing concurrent task", e) }
        }
    }

}
