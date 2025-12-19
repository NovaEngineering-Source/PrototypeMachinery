package github.kasuminova.prototypemachinery.impl.scheduler

import github.kasuminova.prototypemachinery.api.scheduler.ISchedulable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

internal class SchedulerState {
    val mainThreadTasks = ConcurrentHashMap.newKeySet<ISchedulable>()
    val concurrentTasks = ConcurrentHashMap.newKeySet<ISchedulable>()

    val customMainThreadTasks: ConcurrentLinkedQueue<Runnable> = ConcurrentLinkedQueue()
    val customConcurrentTasks: ConcurrentLinkedQueue<Runnable> = ConcurrentLinkedQueue()

    fun clearAll() {
        mainThreadTasks.clear()
        concurrentTasks.clear()
        customMainThreadTasks.clear()
        customConcurrentTasks.clear()
    }
}
