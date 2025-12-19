package github.kasuminova.prototypemachinery.impl.scheduler

internal data class SchedulerRuntimeSettings(
    val backendType: SchedulerBackendType,
    val workerThreads: Int,
    val laneCount: Int,
    val metricsEnabled: Boolean,
    val metricsLogIntervalTicks: Int,
    val metricsWindowTicks: Int,
) {
    companion object {
        fun sane(
            backendType: SchedulerBackendType,
            workerThreads: Int,
            laneCount: Int,
            metricsEnabled: Boolean,
            metricsLogIntervalTicks: Int,
            metricsWindowTicks: Int,
        ): SchedulerRuntimeSettings {
            val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
            val wt = workerThreads.coerceIn(1, cores.coerceAtLeast(2) * 2)
            val lc = laneCount.coerceIn(1, 32)
            val interval = metricsLogIntervalTicks.coerceIn(1, 20 * 60)
            val window = metricsWindowTicks.coerceIn(20, 20 * 300)
            return SchedulerRuntimeSettings(
                backendType = backendType,
                workerThreads = wt,
                laneCount = lc,
                metricsEnabled = metricsEnabled,
                metricsLogIntervalTicks = interval,
                metricsWindowTicks = window,
            )
        }
    }
}
