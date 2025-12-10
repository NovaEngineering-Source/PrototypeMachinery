package github.kasuminova.prototypemachinery.impl.scheduler

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode
import github.kasuminova.prototypemachinery.api.scheduler.ISchedulable
import github.kasuminova.prototypemachinery.api.scheduler.TaskScheduler
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
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

    private fun createExecutorService(): ExecutorService = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    ) { runnable ->
        Thread(runnable, "PrototypeMachinery-Scheduler-${threadCounter.incrementAndGet()}").apply {
            isDaemon = true
            priority = Thread.NORM_PRIORITY + 1
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
        executorService.shutdown()
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
        submitConcurrentTasks()
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
            } catch (e: Exception) {
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
            } catch (e: Exception) {
                PrototypeMachinery.logger.error("Error executing main thread schedulable: ${schedulable.javaClass.simpleName}", e)
            }
        }
    }

    /**
     * Submit concurrent tasks to thread pool.
     * 提交并发任务到线程池。
     */
    private fun submitConcurrentTasks() {
        // Submit custom concurrent tasks
        // 提交自定义并发任务
        var customTask = customConcurrentTasks.poll()
        while (customTask != null) {
            executorService.submit(SafeRunnable(customTask))
            customTask = customConcurrentTasks.poll()
        }

        // Submit concurrent tasks
        // 提交并发可调度任务
        for (schedulable in concurrentTasks) {
            if (!schedulable.isActive()) {
                continue
            }

            executorService.submit(SafeRunnable {
                schedulable.onSchedule()
            })
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
