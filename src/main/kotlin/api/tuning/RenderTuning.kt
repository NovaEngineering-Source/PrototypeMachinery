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

    /** Reuse GL buffer objects (VBO names) instead of deleting/creating them on every BufferBuilder recycle. */
    @Volatile
    public var vboCachePoolEnabled: Boolean = true

    /** Max number of pooled (idle) VBO ids kept for reuse. 0 disables the count limit. */
    @Volatile
    public var vboCachePoolMaxEntries: Int = 256

    /** Max bytes estimate retained by pooled (idle) VBO ids. 0 disables the bytes limit. */
    @Volatile
    public var vboCachePoolMaxBytes: Long = 128L * 1024L * 1024L

    // --- Chunk VBO caching (opaque DEFAULT pass only) ---

    /**
     * Experimental: cache merged opaque geometry per spatial chunk-group to reduce repeated VBO uploads.
     *
     * Only affects RenderPass.DEFAULT (opaque). Transparent/bloom are intentionally excluded.
     */
    @Volatile
    public var opaqueChunkVboCacheEnabled: Boolean = false

    /**
     * Chunk-group size in *chunks* (1 => 1x1, 2 => 2x2, 4 => 4x4). Values outside {1,2,4} are sanitized to 1.
     */
    @Volatile
    public var opaqueChunkVboCacheChunkSize: Int = 1

    /** Max number of cached chunk entries. 0 disables the entry-count limit. */
    @Volatile
    public var opaqueChunkVboCacheMaxEntries: Int = 1024

    /** Max total bytes held by chunk cache (rough estimate, equals uploaded byte sizes). 0 disables this limit. */
    @Volatile
    public var opaqueChunkVboCacheMaxBytes: Long = 256L * 1024L * 1024L

    /** Minimum buffers in a bucket before chunk cache is used. */
    @Volatile
    public var opaqueChunkVboCacheMinBuffers: Int = 2

    /** Minimum total bytes in a bucket before chunk cache is used. */
    @Volatile
    public var opaqueChunkVboCacheMinBytes: Int = 0

    // --- BufferBuilder pooling (direct ByteBuffer retention) ---

    /** If true, the global BufferBuilder pool will evict overly large buffers to avoid unbounded RSS growth. */
    @Volatile
    public var bufferBuilderPoolTrimEnabled: Boolean = true

    /** Do not keep builders whose underlying byteBuffer capacity exceeds this (bytes). 0 disables this limit. */
    @Volatile
    public var bufferBuilderPoolMaxKeepBytes: Int = 64 * 1024 * 1024

    /** Hard upper bound: never keep buffers larger than this in any pool (bytes). 0 disables this limit. */
    @Volatile
    public var bufferBuilderPoolHardMaxKeepBytes: Int = 256 * 1024 * 1024

    /** Enable a tiny oversize pool (above maxKeep) to avoid thrash when large models are rendered repeatedly. */
    @Volatile
    public var bufferBuilderPoolOversizeEnabled: Boolean = true

    /** Max number of oversize builders retained. 0 disables count limit. */
    @Volatile
    public var bufferBuilderPoolOversizeMaxCount: Int = 4

    /** Max total bytes retained in oversize pool. 0 disables this limit. */
    @Volatile
    public var bufferBuilderPoolOversizeMaxTotalBytes: Long = 512L * 1024L * 1024L

    /** Time-to-live for oversize builders in seconds. <=0 disables TTL eviction. */
    @Volatile
    public var bufferBuilderPoolOversizeTtlSeconds: Int = 300

    /** Try to keep pooled builders' total retained bytes under this (bytes). 0 disables this limit. */
    @Volatile
    public var bufferBuilderPoolMaxTotalBytes: Long = 768L * 1024L * 1024L

    // --- Gecko baker hot paths ---

    /** If true, GeckoModelBaker packs POSITION_TEX_COLOR_NORMAL quads and submits via BufferBuilder.addVertexData(int[]). */
    @Volatile
    public var geckoBulkQuadWrite: Boolean = true

    /**
     * If true, GeckoModelBaker will try to use the Java21+ modern-backend vertex pipeline (via reflection)
     * to batch-transform + pack cube vertices, then submit them in one go.
     *
     * Has no effect when the modern-backend classes are not present.
     */
    @Volatile
    public var geckoModernVertexPipeline: Boolean = true

    /** If true, force scalar backend for the modern vertex pipeline (A/B profiling). */
    @Volatile
    public var geckoModernVertexPipelineForceScalar: Boolean = false

    /**
     * Experimental: try to batch multiple *contiguous* cubes under the same bone when cube-local rotation is identity.
     *
     * This can increase effective batch size (more vertices share the same bone matrix), which is where
     * the modern pipeline (especially vectorized backends) can start to pay off.
     */
    @Volatile
    public var geckoBoneBatching: Boolean = false

    /**
     * Minimum vertex count for a contiguous cube-run before we attempt a bone-level batch.
     *
     * Below this, we still apply a cheaper per-cube path (skip cube-local pivot/rotate/back) but do not
     * allocate/fill large SoA buffers.
     */
    @Volatile
    public var geckoBoneBatchingMinVertices: Int = 128

    /**
     * Pre-bake cube-local transforms (pivot/rotation) into cached vertex data.
     *
     * When enabled, cube-local transforms are applied once and cached, so at render time
     * all cubes under the same bone share the same bone matrix. This enables effective
     * bone-level batching for ALL cubes, not just identity-rotation ones.
     *
     * Memory cost: ~24 bytes per vertex (position + normal + uv).
     */
    @Volatile
    public var geckoCubePreBake: Boolean = false

    internal fun sanitize() {
        if (!animStepTicks.isFinite() || animStepTicks <= 0.0) {
            animStepTicks = 0.5
        }
        if (animMaxQueued < 0L) animMaxQueued = 0L
        if (animMaxStressMultiplier < 1) animMaxStressMultiplier = 1
        if (mergeMinBuffers < 1) mergeMinBuffers = 1
        if (mergeMinBytes < 0) mergeMinBytes = 0
        if (vboCachePreferIndividualAboveBytes < 0) vboCachePreferIndividualAboveBytes = 0

        if (opaqueChunkVboCacheMaxEntries < 0) opaqueChunkVboCacheMaxEntries = 0
        if (opaqueChunkVboCacheMaxBytes < 0L) opaqueChunkVboCacheMaxBytes = 0L
        if (opaqueChunkVboCacheMinBuffers < 1) opaqueChunkVboCacheMinBuffers = 1
        if (opaqueChunkVboCacheMinBytes < 0) opaqueChunkVboCacheMinBytes = 0

        opaqueChunkVboCacheChunkSize = when (opaqueChunkVboCacheChunkSize) {
            1, 2, 4 -> opaqueChunkVboCacheChunkSize
            else -> 1
        }
        if (bufferBuilderPoolMaxKeepBytes < 0) bufferBuilderPoolMaxKeepBytes = 0
        if (bufferBuilderPoolHardMaxKeepBytes < 0) bufferBuilderPoolHardMaxKeepBytes = 0
        if (bufferBuilderPoolOversizeMaxCount < 0) bufferBuilderPoolOversizeMaxCount = 0
        if (bufferBuilderPoolOversizeMaxTotalBytes < 0L) bufferBuilderPoolOversizeMaxTotalBytes = 0L
        if (bufferBuilderPoolOversizeTtlSeconds < 0) bufferBuilderPoolOversizeTtlSeconds = 0
        if (bufferBuilderPoolMaxTotalBytes < 0L) bufferBuilderPoolMaxTotalBytes = 0L
        if (geckoBoneBatchingMinVertices < 0) geckoBoneBatchingMinVertices = 0
    }
}
