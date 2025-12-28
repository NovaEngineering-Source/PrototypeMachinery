package github.kasuminova.prototypemachinery.client.impl.render

/**
 * Client-side render stress knobs.
 *
 * These are intentionally simple and are meant for in-game performance experiments.
 * They should not affect correctness when left at defaults.
 */
internal object RenderStress {

    /**
     * Repeat each draw call this many times.
     *
     * - 1: normal rendering
     * - N: multiply GPU vertex processing and draw-call overhead (CPU-side command submission) by ~N
     */
    @Volatile
    var drawMultiplier: Int = 1
        private set

    fun setDrawMultiplier(value: Int) {
        // Keep it sane to avoid accidental hard-freezes.
        drawMultiplier = value.coerceIn(1, 4096)
    }
}
