package github.kasuminova.prototypemachinery.api.tuning

/**
 * Runtime tuning switches for client rendering.
 *
 * Values are kept in API (no Forge dependency) so that:
 * - client render code can read them without touching config classes
 * - common config loader can update them early during startup
 */
public object RenderTuning {

    // --- Animation (render-synced Gecko time keys) ---

    /** Enable render-frame-synced animation time keys. */
    @Volatile
    public var animSmooth: Boolean = false

    /** Quantization step in ticks for animation time keys (e.g. 0.5 => 40Hz). */
    @Volatile
    public var animStepTicks: Double = 0.5

    /** Auto-throttle smooth animation when backlog/stress is high. */
    @Volatile
    public var animAutoThrottle: Boolean = true

    /** If queued build tasks exceed this, smooth animation is suppressed for the frame. */
    @Volatile
    public var animMaxQueued: Long = 0L

    /** If RenderStress.drawMultiplier exceeds this, smooth animation is suppressed. */
    @Volatile
    public var animMaxStressMultiplier: Int = 1

    // --- Merge / batching ---

    /** Minimum buffers in a merge bucket before memcpy+merge is attempted. */
    @Volatile
    public var mergeMinBuffers: Int = 2

    /** Minimum total bytes in a merge bucket before memcpy+merge is attempted. */
    @Volatile
    public var mergeMinBytes: Int = 0

    /** If true, always use client-side arrays even when VBO is available (debug/profiling). */
    @Volatile
    public var mergeForceClientArrays: Boolean = false

    // --- VBO caching (per built BufferBuilder) ---

    /** Enable caching a GPU VBO per built BufferBuilder to avoid per-frame uploads. */
    @Volatile
    public var vboCacheEnabled: Boolean = true

    /**
     * If a merge bucket's total bytes are >= this threshold, prefer drawing buffers individually
     * (using cached VBOs) instead of merge+memcpy+upload.
     */
    @Volatile
    public var vboCachePreferIndividualAboveBytes: Int = 1 * 1024 * 1024

    internal fun sanitize() {
        if (!animStepTicks.isFinite() || animStepTicks <= 0.0) {
            animStepTicks = 0.5
        }
        if (animMaxQueued < 0L) animMaxQueued = 0L
        if (animMaxStressMultiplier < 1) animMaxStressMultiplier = 1
        if (mergeMinBuffers < 1) mergeMinBuffers = 1
        if (mergeMinBytes < 0) mergeMinBytes = 0
        if (vboCachePreferIndividualAboveBytes < 0) vboCachePreferIndividualAboveBytes = 0
    }
}
