package github.kasuminova.prototypemachinery.api.machine.workslot

/**
 * Minimal slot status for UI/debug.
 *
 * 工位的最小状态（用于 UI/调试）。
 */
public enum class WorkSlotStatus {
    IDLE,
    RUNNING,
    BLOCKED,
    FAILED
}
