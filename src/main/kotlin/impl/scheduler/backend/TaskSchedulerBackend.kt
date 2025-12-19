package github.kasuminova.prototypemachinery.impl.scheduler.backend

import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerMetrics
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerRuntimeSettings
import github.kasuminova.prototypemachinery.impl.scheduler.SchedulerState

internal interface TaskSchedulerBackend {
    val backendName: String

    fun onServerTickEnd(state: SchedulerState, metrics: SchedulerMetrics)

    fun reconfigure(settings: SchedulerRuntimeSettings)

    fun shutdown()
}
