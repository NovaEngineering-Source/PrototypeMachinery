package github.kasuminova.prototypemachinery.impl.scheduler

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode
import github.kasuminova.prototypemachinery.api.scheduler.ISchedulable
import github.kasuminova.prototypemachinery.api.scheduler.TaskScheduler
import github.kasuminova.prototypemachinery.common.config.PmSchedulerConfig
import github.kasuminova.prototypemachinery.impl.scheduler.backend.CoroutineTaskSchedulerBackend
import github.kasuminova.prototypemachinery.impl.scheduler.backend.JavaTaskSchedulerBackend
import github.kasuminova.prototypemachinery.impl.scheduler.backend.TaskSchedulerBackend
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

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

    private val state: SchedulerState = SchedulerState()

    @Volatile
    private var isShutdown: Boolean = false

    @Volatile
    private var settings: SchedulerRuntimeSettings = readSettingsFromConfig()

    private val metrics: SchedulerMetrics = SchedulerMetrics(settings.metricsWindowTicks)

    @Volatile
    private var backend: TaskSchedulerBackend = createBackend(settings)

    /**
     * Pending settings applied on next tick boundary.
     * 仅在 tick 边界切换，避免丢任务/破坏语义。
     */
    @Volatile
    private var pendingSettings: SchedulerRuntimeSettings? = null

    /**
     * Runtime check: coroutine classes are provided by a prerequisite mod in your pack.
     * 运行时探测：协程库由前置模组提供；若缺失则禁止启用协程后端。
     */
    private fun isCoroutinesAvailable(): Boolean {
        // Use reflection to avoid hard-crashing when the prerequisite mod is missing.
        return runCatching { Class.forName("kotlinx.coroutines.Job") }.isSuccess
    }

    private fun coerceSettingsForAvailability(s: SchedulerRuntimeSettings): SchedulerRuntimeSettings {
        if (s.backendType != SchedulerBackendType.COROUTINES) return s
        if (isCoroutinesAvailable()) return s

        PrototypeMachinery.logger.warn(
            "Task scheduler: COROUTINES backend requested but kotlinx-coroutines is not available at runtime; falling back to JAVA. " +
                "(Ensure the prerequisite coroutine mod is installed on both server and client.)"
        )
        return s.copy(backendType = SchedulerBackendType.JAVA)
    }

    private fun readSettingsFromConfig(): SchedulerRuntimeSettings {
        val s = PmSchedulerConfig.scheduler
        return SchedulerRuntimeSettings.sane(
            backendType = SchedulerBackendType.parse(s.backend),
            workerThreads = s.workerThreads,
            laneCount = s.laneCount,
            metricsEnabled = s.metricsEnabled,
            metricsLogIntervalTicks = s.metricsLogIntervalTicks,
            metricsWindowTicks = s.metricsWindowTicks,
        )
    }

    private fun createBackend(settings: SchedulerRuntimeSettings): TaskSchedulerBackend {
        return when (settings.backendType) {
            SchedulerBackendType.JAVA -> JavaTaskSchedulerBackend(settings)
            SchedulerBackendType.COROUTINES -> CoroutineTaskSchedulerBackend(settings)
        }
    }

    @Synchronized
    private fun restartIfNeeded() {
        if (!isShutdown) return

        val newSettings = coerceSettingsForAvailability(readSettingsFromConfig())
        settings = newSettings
        metrics.resizeIfNeeded(newSettings.metricsWindowTicks)
        backend = createBackend(newSettings)
        isShutdown = false
        PrototypeMachinery.logger.info("Task scheduler restarted (backend=${backend.backendName})")
    }

    override fun register(schedulable: ISchedulable) {
        restartIfNeeded()
        when (schedulable.getExecutionMode()) {
            ExecutionMode.MAIN_THREAD -> state.mainThreadTasks.add(schedulable)
            ExecutionMode.CONCURRENT -> state.concurrentTasks.add(schedulable)
        }
    }

    override fun unregister(schedulable: ISchedulable) {
        state.mainThreadTasks.remove(schedulable)
        state.concurrentTasks.remove(schedulable)
    }

    override fun submitTask(task: Runnable, executionMode: ExecutionMode) {
        restartIfNeeded()
        when (executionMode) {
            ExecutionMode.MAIN_THREAD -> state.customMainThreadTasks.offer(task)
            ExecutionMode.CONCURRENT -> state.customConcurrentTasks.offer(task)
        }
    }

    override fun getRegisteredCount(): Int = state.mainThreadTasks.size + state.concurrentTasks.size

    /**
     * Request applying current config on next tick.
     */
    internal fun requestReloadFromConfig() {
        pendingSettings = readSettingsFromConfig()
    }

    internal fun requestSwitchBackend(type: SchedulerBackendType): Boolean {
        if (type == SchedulerBackendType.COROUTINES && !isCoroutinesAvailable()) {
            return false
        }
        val s = PmSchedulerConfig.scheduler
        pendingSettings = SchedulerRuntimeSettings.sane(
            backendType = type,
            workerThreads = s.workerThreads,
            laneCount = s.laneCount,
            metricsEnabled = s.metricsEnabled,
            metricsLogIntervalTicks = s.metricsLogIntervalTicks,
            metricsWindowTicks = s.metricsWindowTicks,
        )
        return true
    }

    internal fun currentBackendName(): String = backend.backendName

    internal fun isCoroutinesBackendAvailable(): Boolean = isCoroutinesAvailable()

    internal fun snapshotReport() = metrics.snapshotReport()

    @Synchronized
    internal fun shutdown() {
        if (isShutdown) return

        PrototypeMachinery.logger.info("Shutting down task scheduler (backend=${backend.backendName})...")
        isShutdown = true

        backend.shutdown()
        state.clearAll()

        PrototypeMachinery.logger.info("Task scheduler shutdown complete")
    }

    @SubscribeEvent
    internal fun onServerTick(event: TickEvent.ServerTickEvent) {
        if (event.phase != TickEvent.Phase.END || isShutdown) {
            return
        }

        val ps = pendingSettings
        if (ps != null) {
            applyPendingSettings(ps)
        }

        backend.onServerTickEnd(state, metrics)
        metrics.maybeLog(settings.metricsLogIntervalTicks, settings.metricsEnabled)
    }

    @Synchronized
    private fun applyPendingSettings(ps: SchedulerRuntimeSettings) {
        // Double-check inside lock.
        val pending = pendingSettings ?: return
        if (pending != ps) return

        pendingSettings = null
        val effective = coerceSettingsForAvailability(ps)
        settings = effective
        metrics.resizeIfNeeded(effective.metricsWindowTicks)

        // Swap backend at tick boundary. Keep SchedulerState untouched to avoid losing registrations.
        runCatching { backend.shutdown() }
        backend = createBackend(effective)

        PrototypeMachinery.logger.warn(
            "Task scheduler backend switched to ${backend.backendName} (threads=${effective.workerThreads}, lanes=${effective.laneCount})"
        )
    }
}
