package github.kasuminova.prototypemachinery.api.tuning

/**
 * Runtime tuning switches for client rendering.
 *
 * Values are kept in API (no Forge dependency) so that:
 * - client render code can read them without touching config classes
 * - common config loader can update them early during startup
 */
public object RenderTuning {

    /** Scratch merged VBO upload modes for [github.kasuminova.prototypemachinery.client.util.ReusableVboUploader]. */
    public object ScratchVboUploadMode {
        /** Classic upload: glBuffer(Sub)Data (optionally orphaning). */
        public const val SUB_DATA: Int = 0

        /** Upload via glMapBufferRange + memcpy into the mapped range. */
        public const val MAP_RANGE_UNSYNC: Int = 1
    }

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

    // --- Render-build task scheduling ---

    /**
     * Experimental: if true, render-build tasks are executed via Kotlin coroutines on a dedicated fixed thread pool.
     *
     * Default is false (use dedicated ForkJoinPool). This exists mainly as a fallback when certain runtimes
     * exhibit pathological scheduling behavior with other executors.
     */
    @Volatile
    public var renderBuildUseCoroutines: Boolean = false

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

    /**
     * If true, merged draws may upload each source builder directly into a scratch VBO via
     * glBufferSubData(offset, data) slices, avoiding an intermediate CPU merge buffer.
     *
     * This reduces memcpy/merge self-time at the cost of extra GL calls (one per source segment).
     */
    @Volatile
    public var mergeDirectVboSliceUploadEnabled: Boolean = false

    /** Minimum total bytes before we consider the VBO slice upload path. */
    @Volatile
    public var mergeDirectVboSliceUploadMinBytes: Int = 512 * 1024

    /** Minimum source buffers before we consider the VBO slice upload path. */
    @Volatile
    public var mergeDirectVboSliceUploadMinSegments: Int = 2

    /**
     * If true, merging multiple BufferBuilders into a scratch buffer may use parallel copy.
     *
     * This targets the self-time of `ReusableVboUploader.drawMultiple*` when memcpy dominates.
     * Implementation uses non-overlapping ByteBuffer slices and a ForkJoin join barrier.
     */
    @Volatile
    public var mergeParallelCopyEnabled: Boolean = true

    /** Minimum total bytes in a merge bucket before we consider parallel copy. */
    @Volatile
    public var mergeParallelCopyMinBytes: Int = 2 * 1024 * 1024

    /** Minimum number of source buffers (segments) before we consider parallel copy. */
    @Volatile
    public var mergeParallelCopyMinSegments: Int = 8

    /**
     * Usage hint for the uploader's scratch merged VBO uploads.
     *
     * Vanilla VertexBuffer.bufferData uses GL_STATIC_DRAW (35044). For per-frame dynamic merged uploads,
     * GL_STREAM_DRAW (35040) is often a better hint.
     *
     * Allowed: 35040 (STREAM), 35044 (STATIC), 35048 (DYNAMIC)
     */
    @Volatile
    public var mergeScratchVboUsage: Int = 35040

    /**
     * Ring size for scratch merged VBO uploads.
     *
     * Why:
     * - We often upload+draw many merged buckets per frame.
     * - Re-uploading into the same VBO object can force driver stalls when previous draws still reference it.
     * - Using a small ring (N VBOs) reduces overwrite hazards and lets the driver pipeline work better.
     *
    * 2 = classic double-buffering. 4+ is recommended for smoother pipelining under heavy load.
     */
    @Volatile
    public var mergeScratchVboRingSize: Int = 4

    /**
     * If true, use the classic "orphan then subData" upload pattern:
     * - glBufferData(size, null) to allocate fresh storage
     * - glBufferSubData(0, data)
     *
     * This can further reduce stalls on some drivers when streaming dynamic data.
     */
    @Volatile
    public var mergeScratchVboOrphaningEnabled: Boolean = true

    /**
     * Scratch VBO upload mode.
     *
     * Default is [ScratchVboUploadMode.SUB_DATA].
     *
     * Notes:
     * - [ScratchVboUploadMode.MAP_RANGE_UNSYNC] uses glMapBufferRange with UNSYNCHRONIZED_BIT when supported.
    * - This implementation always orphans the buffer storage before mapping, so overwrite hazards are handled by the driver.
     */
    @Volatile
    public var mergeScratchVboUploadMode: Int = ScratchVboUploadMode.SUB_DATA

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

    /**
     * When VBO cache is enabled, if a merge bucket already has at least this fraction of buffers cached,
     * prefer drawing individually (cached VBO path) to avoid merge+upload churn for mostly-static geometry.
     *
     * Range: 0.0 .. 1.0
     */
    @Volatile
    public var vboCachePreferIndividualWhenCachedRatio: Double = 0.5

    /** Reuse GL buffer objects (VBO names) instead of deleting/creating them on every BufferBuilder recycle. */
    @Volatile
    public var vboCachePoolEnabled: Boolean = true

    /** Max number of pooled (idle) VBO ids kept for reuse. 0 disables the count limit. */
    @Volatile
    public var vboCachePoolMaxEntries: Int = 256

    /** Max bytes estimate retained by pooled (idle) VBO ids. 0 disables the bytes limit. */
    @Volatile
    public var vboCachePoolMaxBytes: Long = 128L * 1024L * 1024L

    // --- BufferBuilder pooling (direct ByteBuffer retention) ---

    /** If true, the global BufferBuilder pool will evict overly large buffers to avoid unbounded RSS growth. */
    @Volatile
    public var bufferBuilderPoolTrimEnabled: Boolean = true

    // --- Direct ByteBuffer pooling (for async packing/merge) ---

    /** Reuse direct ByteBuffers to reduce Cleaner/GC pressure when async packing is enabled. */
    @Volatile
    public var directByteBufferPoolEnabled: Boolean = true

    /** Max number of idle pooled direct ByteBuffers kept. 0 disables entry-count limit. */
    @Volatile
    public var directByteBufferPoolMaxEntries: Int = 64

    /** Max total bytes kept by the direct ByteBuffer pool. 0 disables the bytes limit. */
    @Volatile
    public var directByteBufferPoolMaxBytes: Long = 256L * 1024L * 1024L

    // --- Async bucket packing (hide merge cost off-thread) ---

    /**
     * If true, the dispatcher may pack uncached (dynamic) DEFAULT buckets off-thread,
     * then render from the last completed packed buffer (or skip) for a few frames.
     */
    @Volatile
    public var asyncUncachedBucketPackEnabled: Boolean = false

    /** Minimum bytes in a bucket before we submit an async pack task. */
    @Volatile
    public var asyncUncachedBucketPackMinBytes: Int = 512 * 1024

    /** Minimum buffers in a bucket before we submit an async pack task. */
    @Volatile
    public var asyncUncachedBucketPackMinBuffers: Int = 2

    /**
     * Maximum bytes for a single packed output buffer (part). <=0 disables splitting.
     *
     * Allowing a few parts can reduce peak direct allocation and improve packing locality.
     */
    @Volatile
    public var asyncUncachedBucketPackMaxPartBytes: Int = 8 * 1024 * 1024

    /** Maximum number of parts produced by an async pack task. Minimum is 1. */
    @Volatile
    public var asyncUncachedBucketPackMaxParts: Int = 4

    /** Max allowed render lag (frames) for reusing last-completed packed buffers. */
    @Volatile
    public var asyncUncachedBucketPackMaxLagFrames: Int = 3

    /** If true, render last-completed packed data while a new pack task is in-flight. */
    @Volatile
    public var asyncUncachedBucketPackUseLastComplete: Boolean = true

    /**
     * If true, when no packed data is available yet, fall back to immediate draw (merge on render thread).
     * If false, the bucket is skipped until the first packed result is ready.
     */
    @Volatile
    public var asyncUncachedBucketPackFallbackImmediateDraw: Boolean = false

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

    // --- Gecko baker ---

    /**
     * For GeckoModelBaker ANIMATED_ONLY builds: minimum (estimated) total bytes before using direct packed buffers
     * (PackedBucketBatch) instead of BufferBuilder.
     *
     * 0 = always attempt. Set to a very large value to effectively disable.
     */
    @Volatile
    public var geckoDirectPackedBuffersMinBytes: Int = 256 * 1024

    /**
     * Experimental: for GeckoModelBaker ANIMATED_ONLY builds, try to write packed vertex data directly into a mapped
     * VBO (A/B buffered per owner+pass), avoiding CPU-side intermediate buffers and the upload memcpy.
     */
    @Volatile
    public var geckoDirectMappedVboEnabled: Boolean = false

    /** Minimum (estimated) total bytes before enabling mapped-VBO direct write. 0 = always attempt. */
    @Volatile
    public var geckoDirectMappedVboMinBytes: Int = 512 * 1024

    internal fun sanitize() {
        if (!animStepTicks.isFinite() || animStepTicks <= 0.0) {
            animStepTicks = 0.5
        }
        if (animMaxQueued < 0L) animMaxQueued = 0L
        if (animMaxStressMultiplier < 1) animMaxStressMultiplier = 1
        if (mergeMinBuffers < 1) mergeMinBuffers = 1
        if (mergeMinBytes < 0) mergeMinBytes = 0

        if (mergeDirectVboSliceUploadMinBytes < 0) mergeDirectVboSliceUploadMinBytes = 0
        if (mergeDirectVboSliceUploadMinBytes > 512 * 1024 * 1024) mergeDirectVboSliceUploadMinBytes = 512 * 1024 * 1024
        if (mergeDirectVboSliceUploadMinSegments < 1) mergeDirectVboSliceUploadMinSegments = 1
        if (mergeDirectVboSliceUploadMinSegments > 4096) mergeDirectVboSliceUploadMinSegments = 4096

        if (mergeParallelCopyMinBytes < 0) mergeParallelCopyMinBytes = 0
        if (mergeParallelCopyMinSegments < 1) mergeParallelCopyMinSegments = 1

        // Upload usage hint: keep it in the common well-known set.
        mergeScratchVboUsage = when (mergeScratchVboUsage) {
            35040, 35044, 35048 -> mergeScratchVboUsage
            else -> 35040
        }

        mergeScratchVboUploadMode = when (mergeScratchVboUploadMode) {
            ScratchVboUploadMode.SUB_DATA, ScratchVboUploadMode.MAP_RANGE_UNSYNC -> mergeScratchVboUploadMode
            else -> ScratchVboUploadMode.SUB_DATA
        }

        if (mergeScratchVboRingSize < 1) mergeScratchVboRingSize = 1
        if (mergeScratchVboRingSize > 64) mergeScratchVboRingSize = 64
        if (vboCachePreferIndividualAboveBytes < 0) vboCachePreferIndividualAboveBytes = 0

        if (vboCachePreferIndividualWhenCachedRatio.isNaN()) vboCachePreferIndividualWhenCachedRatio = 0.5
        if (vboCachePreferIndividualWhenCachedRatio < 0.0) vboCachePreferIndividualWhenCachedRatio = 0.0
        if (vboCachePreferIndividualWhenCachedRatio > 1.0) vboCachePreferIndividualWhenCachedRatio = 1.0

        if (bufferBuilderPoolMaxKeepBytes < 0) bufferBuilderPoolMaxKeepBytes = 0
        if (bufferBuilderPoolHardMaxKeepBytes < 0) bufferBuilderPoolHardMaxKeepBytes = 0
        if (bufferBuilderPoolOversizeMaxCount < 0) bufferBuilderPoolOversizeMaxCount = 0
        if (bufferBuilderPoolOversizeMaxTotalBytes < 0L) bufferBuilderPoolOversizeMaxTotalBytes = 0L
        if (bufferBuilderPoolOversizeTtlSeconds < 0) bufferBuilderPoolOversizeTtlSeconds = 0
        if (bufferBuilderPoolMaxTotalBytes < 0L) bufferBuilderPoolMaxTotalBytes = 0L

        if (directByteBufferPoolMaxEntries < 0) directByteBufferPoolMaxEntries = 0
        if (directByteBufferPoolMaxBytes < 0L) directByteBufferPoolMaxBytes = 0L

        if (asyncUncachedBucketPackMinBytes < 0) asyncUncachedBucketPackMinBytes = 0
        if (asyncUncachedBucketPackMinBuffers < 1) asyncUncachedBucketPackMinBuffers = 1

        if (asyncUncachedBucketPackMaxPartBytes < 0) asyncUncachedBucketPackMaxPartBytes = 0
        if (asyncUncachedBucketPackMaxPartBytes > 512 * 1024 * 1024) asyncUncachedBucketPackMaxPartBytes = 512 * 1024 * 1024

        if (asyncUncachedBucketPackMaxParts < 1) asyncUncachedBucketPackMaxParts = 1
        if (asyncUncachedBucketPackMaxParts > 64) asyncUncachedBucketPackMaxParts = 64

        if (asyncUncachedBucketPackMaxLagFrames < 0) asyncUncachedBucketPackMaxLagFrames = 0

        if (geckoDirectPackedBuffersMinBytes < 0) geckoDirectPackedBuffersMinBytes = 0
        if (geckoDirectPackedBuffersMinBytes > 512 * 1024 * 1024) geckoDirectPackedBuffersMinBytes = 512 * 1024 * 1024

        if (geckoDirectMappedVboMinBytes < 0) geckoDirectMappedVboMinBytes = 0
        if (geckoDirectMappedVboMinBytes > 512 * 1024 * 1024) geckoDirectMappedVboMinBytes = 512 * 1024 * 1024
    }
}
