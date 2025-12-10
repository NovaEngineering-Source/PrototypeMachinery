package github.kasuminova.prototypemachinery.api.scheduler

/**
 * Central scheduler API for managing task execution.
 * 用于管理任务执行的中央调度器 API。
 *
 * The scheduler handles periodic execution of registered schedulable tasks,
 * supporting both main thread and concurrent execution modes.
 *
 * 调度器处理已注册可调度任务的周期性执行，
 * 支持主线程和并发执行模式。
 */
public interface TaskScheduler {

    /**
     * Register a schedulable task.
     * 注册可调度任务。
     *
     * @param schedulable The task to register
     * @param schedulable 要注册的任务
     */
    public fun register(schedulable: ISchedulable)

    /**
     * Unregister a schedulable task.
     * 取消注册可调度任务。
     *
     * @param schedulable The task to unregister
     * @param schedulable 要取消注册的任务
     */
    public fun unregister(schedulable: ISchedulable)

    /**
     * Submit a custom task for execution.
     * 提交自定义任务以供执行。
     *
     * @param task The task to execute
     * @param executionMode The execution mode (main thread or concurrent)
     * @param task 要执行的任务
     * @param executionMode 执行模式（主线程或并发）
     */
    public fun submitTask(task: Runnable, executionMode: ExecutionMode)

    /**
     * Get the number of registered schedulable tasks.
     * 获取已注册可调度任务的数量。
     *
     * @return The number of registered tasks
     * @return 已注册任务的数量
     */
    public fun getRegisteredCount(): Int

}
