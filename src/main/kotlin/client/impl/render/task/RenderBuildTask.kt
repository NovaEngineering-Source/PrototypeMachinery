package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.impl.render.RenderStats
import java.util.concurrent.RecursiveAction

/**
 * Background task that builds CPU-side buffers for a given renderable instance.
 *
 * Concrete implementations should:
 * - Read/parse assets via a resolver (optionally decrypting)
 * - Evaluate animations at tick-rate
 * - Fill BufferBuilder instances by render pass
 */
internal abstract class RenderBuildTask(
    internal val ownerKey: Any,
) : RecursiveAction() {

    @Volatile
    internal var built: BuiltBuffers? = null
        private set

    @Volatile
    internal var builtForKey: RenderKey? = null
        private set

    @Volatile
    internal var lastError: Throwable? = null
        private set

    internal fun isReadyFor(key: RenderKey): Boolean = built != null && builtForKey == key

    internal fun takeBuilt(): BuiltBuffers? = built

    internal fun clearBuilt() {
        built?.disposeToPool()
        built = null
        builtForKey = null
        lastError = null
    }

    override fun compute() {
        val timing = RenderStats.enabled
        val t0 = if (timing) System.nanoTime() else 0L
        try {
            val key = currentKey()
            val result = build(key)
            builtForKey = key
            built = result

            if (timing) {
                val dt = System.nanoTime() - t0
                RenderStats.addRenderBuildTaskNanos(dt, success = true)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
            lastError = t
            built = null
            builtForKey = null

            if (timing) {
                val dt = System.nanoTime() - t0
                RenderStats.addRenderBuildTaskNanos(dt, success = false)
            }
        }
    }

    /** The render key snapshot used for this build. */
    protected abstract fun currentKey(): RenderKey

    /** Build buffers for the given [key]. Runs on a background thread. */
    protected abstract fun build(key: RenderKey): BuiltBuffers
}
