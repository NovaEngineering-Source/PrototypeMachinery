package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache.remove
import java.util.concurrent.atomic.AtomicLong

/**
 * Per-instance task cache keyed by [Renderable.ownerKey].
 *
 * NOTE: We use a regular HashMap instead of WeakHashMap because our ownerKey is a data class
 * created on every frame. WeakHashMap would lose entries when the temporary key instance is GC'd,
 * even though equals() matches. Instead, we rely on explicit cleanup via [remove] when TEs unload.
 */
internal object RenderTaskCache {

    internal data class StatsSnapshot(
        val getOrSubmitCalls: Long,
        val hitCurrentReady: Long,
        val hitCurrentBuilding: Long,
        val submittedCurrent: Long,
        val submittedNext: Long,
        val promotedNextReady: Long,
        val promotedNextCompatible: Long,
        val keptCurrentWhileNextBuilding: Long,
        val droppedNextDoneNotReady: Long,
    )

    private val getOrSubmitCalls = AtomicLong(0)
    private val hitCurrentReady = AtomicLong(0)
    private val hitCurrentBuilding = AtomicLong(0)
    private val submittedCurrent = AtomicLong(0)
    private val submittedNext = AtomicLong(0)
    private val promotedNextReady = AtomicLong(0)
    private val promotedNextCompatible = AtomicLong(0)
    private val keptCurrentWhileNextBuilding = AtomicLong(0)
    private val droppedNextDoneNotReady = AtomicLong(0)

    internal fun statsSnapshot(): StatsSnapshot = StatsSnapshot(
        getOrSubmitCalls = getOrSubmitCalls.get(),
        hitCurrentReady = hitCurrentReady.get(),
        hitCurrentBuilding = hitCurrentBuilding.get(),
        submittedCurrent = submittedCurrent.get(),
        submittedNext = submittedNext.get(),
        promotedNextReady = promotedNextReady.get(),
        promotedNextCompatible = promotedNextCompatible.get(),
        keptCurrentWhileNextBuilding = keptCurrentWhileNextBuilding.get(),
        droppedNextDoneNotReady = droppedNextDoneNotReady.get(),
    )

    /**
     * Current task per owner.
     *
     * IMPORTANT: Do NOT aggressively replace a finished-but-stale task for animated keys.
     * We want to keep rendering the last built buffers while a new build is in-flight,
     * otherwise models can disappear for most frames (especially when build > tick time).
     */
    private val tasks: MutableMap<Any, RenderBuildTask> = HashMap()

    /**
     * Next task per owner, used as a "back buffer" when keys change.
     *
     * This enables "render old while building new" semantics.
     */
    private val nextTasks: MutableMap<Any, RenderBuildTask> = HashMap()

    internal data class SizeSnapshot(
        val tasks: Int,
        val nextTasks: Int,
    )

    internal fun sizeSnapshot(): SizeSnapshot = SizeSnapshot(
        tasks = tasks.size,
        nextTasks = nextTasks.size,
    )

    internal fun get(ownerKey: Any): RenderBuildTask? = tasks[ownerKey]

    internal fun put(ownerKey: Any, task: RenderBuildTask) {
        tasks[ownerKey] = task
    }

    internal fun remove(ownerKey: Any): RenderBuildTask? {
        val n = nextTasks.remove(ownerKey)
        n?.clearBuilt()
        val t = tasks.remove(ownerKey)
        t?.clearBuilt()
        return t
    }

    /**
     * Remove all cached tasks whose ownerKey contains the given TE.
     *
     * This is called when a TileEntity is invalidated to avoid memory leaks.
     */
    internal fun removeByTe(te: Any) {
        val keysToRemove = tasks.keys.filter { key ->
            when (key) {
                is Iterable<*> -> key.any { it === te }
                is Array<*> -> key.any { it === te }
                else -> {
                    // For data classes containing the TE, we check via reflection-free duck typing.
                    // Our RenderOwnerKey is a data class with te as the first field.
                    try {
                        val field = key.javaClass.getDeclaredField("te")
                        field.isAccessible = true
                        field.get(key) === te
                    } catch (_: Exception) {
                        false
                    }
                }
            }
        }
        keysToRemove.forEach { key ->
            tasks.remove(key)?.clearBuilt()
            nextTasks.remove(key)?.clearBuilt()
        }
    }

    /** Clear all cached tasks (e.g. on script reload). */
    internal fun clearAll() {
        for (t in tasks.values) {
            t.clearBuilt()
        }
        for (t in nextTasks.values) {
            t.clearBuilt()
        }
        tasks.clear()
        nextTasks.clear()
    }

    /**
     * Get or create a task for this renderable.
     *
     * If an existing task is not ready for the current [Renderable.renderKey], a new task is created
     * because snapshot data (e.g., orientation) is baked into the task at creation time.
     */
    internal fun getOrSubmit(renderable: Renderable, factory: () -> RenderBuildTask): RenderBuildTask {
        getOrSubmitCalls.incrementAndGet()
        val owner = renderable.ownerKey
        val key: RenderKey = renderable.renderKey

        val current = tasks[owner]

        // No current task: create one.
        if (current == null) {
            val created = factory()
            tasks[owner] = created
            submittedCurrent.incrementAndGet()
            return RenderTaskExecutor.submit(created)
        }

        // Current task still building: keep it.
        if (!current.isDone) {
            hitCurrentBuilding.incrementAndGet()
            return current
        }

        // Current task is ready for the desired key: use it.
        if (current.isReadyFor(key)) {
            hitCurrentReady.incrementAndGet()
            return current
        }

        // Current task is done but stale.
        // If we have a next task building/ready, prefer switching when ready.
        val next = nextTasks[owner]
        if (next != null) {
            if (next.isReadyFor(key)) {
                // Promote next to current.
                nextTasks.remove(owner)
                current.clearBuilt()
                tasks[owner] = next
                promotedNextReady.incrementAndGet()
                return next
            }
            if (!next.isDone) {
                // Next is building; keep rendering current if it has something.
                keptCurrentWhileNextBuilding.incrementAndGet()
                return if (current.takeBuilt() != null) current else next
            }

            // next is done but still not ready for this key.
            // If the base render key matches (only animation time differs), we can still promote it
            // to advance animation without causing pass-to-pass skew.
            val nextKey = next.builtForKey
            if (nextKey != null && baseEqualsIgnoreAnimTime(nextKey, key)) {
                nextTasks.remove(owner)
                current.clearBuilt()
                tasks[owner] = next
                promotedNextCompatible.incrementAndGet()
                return next
            }

            // Otherwise drop and create a new one below.
            nextTasks.remove(owner)?.clearBuilt()
            droppedNextDoneNotReady.incrementAndGet()
        }

        // Start building a new snapshot for this key.
        val created = factory()
        nextTasks[owner] = created
        submittedNext.incrementAndGet()
        RenderTaskExecutor.submit(created)

        // Keep rendering old result while new build is running.
        return if (current.takeBuilt() != null) current else created
    }

    private fun baseEqualsIgnoreAnimTime(a: RenderKey, b: RenderKey): Boolean {
        return a.modelId == b.modelId &&
            a.textureId == b.textureId &&
            a.variant == b.variant &&
            a.secureVersion == b.secureVersion &&
            a.flags == b.flags &&
            a.orientationHash == b.orientationHash
    }
}
