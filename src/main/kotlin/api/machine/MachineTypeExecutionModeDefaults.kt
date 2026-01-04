package github.kasuminova.prototypemachinery.api.machine

import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode

/**
 * Optional capability for a [MachineType] to provide a default [ExecutionMode].
 *
 * Used by system components (e.g. SchedulingModeComponent) to decide how a machine instance
 * should be scheduled when there is no per-instance override.
 */
public interface MachineTypeExecutionModeDefaults {

    /**
     * Default execution mode for machine logic.
     *
     * Defaults to [ExecutionMode.CONCURRENT] for performance.
     */
    public val defaultExecutionMode: ExecutionMode
        get() = ExecutionMode.CONCURRENT
}
