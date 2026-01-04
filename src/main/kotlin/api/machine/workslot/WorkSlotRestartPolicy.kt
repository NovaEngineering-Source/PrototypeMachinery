package github.kasuminova.prototypemachinery.api.machine.workslot

/**
 * Restart policy when a work slot cannot make progress.
 *
 * 工位无法推进时的重启策略。
 */
public enum class WorkSlotRestartPolicy {

    /**
     * Keep the current recipe/process and wait until it can continue.
     *
     * 保持当前配方/进程，持续等待直到条件满足。
     */
    LOCKED_RETRY,

    /**
     * Drop the current process after some delay/cooldown and rescan for another recipe.
     *
     * 在一定延迟/冷却后丢弃当前进程，并重新扫描选择配方。
     */
    RESCAN
}
