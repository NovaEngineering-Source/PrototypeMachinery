package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import java.util.WeakHashMap

/**
 * Per-instance task cache keyed by [Renderable.ownerKey].
 *
 * This mirrors MMCE's "tasks = WeakHashMap<Tile, Task>" pattern.
 */
internal object RenderTaskCache {

    private val tasks: MutableMap<Any, RenderBuildTask> = WeakHashMap()

    internal fun get(ownerKey: Any): RenderBuildTask? = tasks[ownerKey]

    internal fun put(ownerKey: Any, task: RenderBuildTask) {
        tasks[ownerKey] = task
    }

    internal fun remove(ownerKey: Any): RenderBuildTask? = tasks.remove(ownerKey)

    /** Clear all cached tasks (e.g. on script reload). */
    internal fun clearAll() {
        tasks.clear()
    }

    /**
     * Get or create a task for this renderable.
     *
     * If an existing task is not ready for the current [Renderable.renderKey], a new task is created
     * because snapshot data (e.g., orientation) is baked into the task at creation time.
     */
    internal fun getOrSubmit(renderable: Renderable, factory: () -> RenderBuildTask): RenderBuildTask {
        val owner = renderable.ownerKey
        val key: RenderKey = renderable.renderKey

        val existing = tasks[owner]
        if (existing != null) {
            if (!existing.isReadyFor(key) && !existing.isDone) {
                // still running; keep it
                return existing
            }
            if (existing.isReadyFor(key)) {
                // finished and still valid
                return existing
            }
            // finished but stale (e.g., orientation changed) - create a new task with fresh snapshot
        }

        val created = factory()
        tasks[owner] = created
        return RenderTaskExecutor.submit(created)
    }
}
