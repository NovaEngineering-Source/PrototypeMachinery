package github.kasuminova.prototypemachinery.api.scheduler

/**
 * Thread execution mode for schedulable tasks.
 * 可调度任务的线程执行模式。
 */
public enum class ExecutionMode {
    /**
     * Execute on the main server thread.
     * 在主服务器线程上执行。
     */
    MAIN_THREAD,

    /**
     * Execute on a concurrent thread pool.
     * 在并发线程池中执行。
     */
    CONCURRENT
}

/**
 * Interface for objects that can be scheduled for periodic execution.
 * 可调度周期性执行的对象接口。
 *
 * Unlike ITickable, this interface allows the task to specify its execution mode.
 * 与 ITickable 不同，此接口允许任务指定其执行模式。
 */
public interface ISchedulable {

    /**
     * Called periodically by the scheduler.
     * 由调度器周期性调用。
     *
     * The frequency depends on the scheduler implementation.
     * 频率取决于调度器实现。
     */
    public fun onSchedule()

    /**
     * Specifies the execution mode for this schedulable task.
     * 指定此可调度任务的执行模式。
     *
     * @return The preferred execution mode
     * @return 首选执行模式
     */
    public fun getExecutionMode(): ExecutionMode

    /**
     * Whether this schedulable task is currently active and should be executed.
     * 此可调度任务当前是否活动并应被执行。
     *
     * @return true if the task should be executed, false otherwise
     * @return 如果任务应被执行则返回 true，否则返回 false
     */
    public fun isActive(): Boolean = true

}
