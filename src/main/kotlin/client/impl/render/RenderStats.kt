package github.kasuminova.prototypemachinery.client.impl.render

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight per-frame render stats for debugging/perf HUD.
 *
 * All counters are updated from the render thread, but we use atomics so they are safe to read from
 * the overlay event (also render thread) and resilient to accidental cross-thread reads.
 */
internal object RenderStats {

    /**
     * When false, all stat updates are no-ops to avoid any overhead in hot paths.
     *
     * This is driven by [RenderDebugHud.enabled] once per frame.
     */
    @Volatile
    internal var enabled: Boolean = false

    internal data class Snapshot(
        val drawCalls: Long = 0,
        val vertices: Long = 0,
        val batches: Long = 0,
        val mergedBuffers: Long = 0,
        val mergedBytes: Long = 0,
        val mergeBuckets: Long = 0,
        val mergeBucketTotalSize: Long = 0,
        val mergeBucketMaxSize: Long = 0,
        val vboUploads: Long = 0,
        val vboUploadBytes: Long = 0,
        val vboCacheHits: Long = 0,
        val vboCacheMisses: Long = 0,

        // Opaque chunk VBO cache (DEFAULT only)
        val opaqueChunkCacheHits: Long = 0,
        val opaqueChunkCacheMisses: Long = 0,
        val opaqueChunkCacheEvictions: Long = 0,
        val opaqueChunkCacheUploads: Long = 0,
        val opaqueChunkCacheUploadBytes: Long = 0,
        val geckoQuads: Long = 0,
        val geckoVertices: Long = 0,
        val geckoBulkQuads: Long = 0,
        val geckoFallbackQuads: Long = 0,
        val geckoLegacyQuads: Long = 0,
        val geckoPipelineBatches: Long = 0,
        val geckoPipelineQuads: Long = 0,
        val geckoPipelineVertices: Long = 0,

        // Pipeline batch-size histogram (counts per frame).
        val geckoPipelineBatchLe32: Long = 0,
        val geckoPipelineBatchLe64: Long = 0,
        val geckoPipelineBatchLe128: Long = 0,
        val geckoPipelineBatchLe256: Long = 0,
        val geckoPipelineBatchLe512: Long = 0,
        val geckoPipelineBatchLe1024: Long = 0,
        val geckoPipelineBatchGt1024: Long = 0,
        val geckoQuadsTotal: Long = 0,
        val geckoVerticesTotal: Long = 0,
        val geckoBulkQuadsTotal: Long = 0,
        val geckoFallbackQuadsTotal: Long = 0,
        val geckoLegacyQuadsTotal: Long = 0,
        val geckoPipelineBatchesTotal: Long = 0,
        val geckoPipelineQuadsTotal: Long = 0,
        val geckoPipelineVerticesTotal: Long = 0,

        // Pipeline batch-size histogram (cumulative totals while HUD is enabled).
        val geckoPipelineBatchLe32Total: Long = 0,
        val geckoPipelineBatchLe64Total: Long = 0,
        val geckoPipelineBatchLe128Total: Long = 0,
        val geckoPipelineBatchLe256Total: Long = 0,
        val geckoPipelineBatchLe512Total: Long = 0,
        val geckoPipelineBatchLe1024Total: Long = 0,
        val geckoPipelineBatchGt1024Total: Long = 0,
        val geckoPipelineBackend: String = "",
        val geckoPipelineVectorized: Boolean = false,

        // Timing (best-effort, debug only)
        val geckoPipelineNanos: Long = 0,
        val geckoPipelineNanosTotal: Long = 0,
        val geckoQuadBulkNanos: Long = 0,
        val geckoQuadBulkNanosTotal: Long = 0,
        val geckoQuadLegacyNanos: Long = 0,
        val geckoQuadLegacyNanosTotal: Long = 0,
        val geckoQuadBulkVerticesTimed: Long = 0,
        val geckoQuadBulkVerticesTimedTotal: Long = 0,
        val geckoQuadLegacyVerticesTimed: Long = 0,
        val geckoQuadLegacyVerticesTimedTotal: Long = 0,

        // Cube pre-bake cache stats
        val geckoCubeCacheHits: Long = 0,
        val geckoCubeCacheMisses: Long = 0,
        val geckoCubeCacheSize: Int = 0,

        val textureBinds: Long = 0,
        val dispatcherPending: Long = 0,
        val renderManagerBuckets: Long = 0,
    )

    private val drawCalls = AtomicLong(0)
    private val vertices = AtomicLong(0)
    private val batches = AtomicLong(0)
    private val mergedBuffers = AtomicLong(0)
    private val mergedBytes = AtomicLong(0)

    private val mergeBuckets = AtomicLong(0)
    private val mergeBucketTotalSize = AtomicLong(0)
    private val mergeBucketMaxSize = AtomicLong(0)

    private val vboUploads = AtomicLong(0)
    private val vboUploadBytes = AtomicLong(0)

    private val vboCacheHits = AtomicLong(0)
    private val vboCacheMisses = AtomicLong(0)

    private val opaqueChunkCacheHits = AtomicLong(0)
    private val opaqueChunkCacheMisses = AtomicLong(0)
    private val opaqueChunkCacheEvictions = AtomicLong(0)
    private val opaqueChunkCacheUploads = AtomicLong(0)
    private val opaqueChunkCacheUploadBytes = AtomicLong(0)

    private val geckoQuads = AtomicLong(0)
    private val geckoVertices = AtomicLong(0)
    private val geckoBulkQuads = AtomicLong(0)
    private val geckoFallbackQuads = AtomicLong(0)
    private val geckoLegacyQuads = AtomicLong(0)

    private val geckoPipelineBatches = AtomicLong(0)
    private val geckoPipelineQuads = AtomicLong(0)
    private val geckoPipelineVertices = AtomicLong(0)

    // Pipeline batch-size histogram (counts of batches).
    private val geckoPipelineBatchLe32 = AtomicLong(0)
    private val geckoPipelineBatchLe64 = AtomicLong(0)
    private val geckoPipelineBatchLe128 = AtomicLong(0)
    private val geckoPipelineBatchLe256 = AtomicLong(0)
    private val geckoPipelineBatchLe512 = AtomicLong(0)
    private val geckoPipelineBatchLe1024 = AtomicLong(0)
    private val geckoPipelineBatchGt1024 = AtomicLong(0)

    // Cumulative totals while HUD is enabled (useful because baking is bursty).
    private val geckoQuadsTotal = AtomicLong(0)
    private val geckoVerticesTotal = AtomicLong(0)
    private val geckoBulkQuadsTotal = AtomicLong(0)
    private val geckoFallbackQuadsTotal = AtomicLong(0)
    private val geckoLegacyQuadsTotal = AtomicLong(0)

    private val geckoPipelineBatchesTotal = AtomicLong(0)
    private val geckoPipelineQuadsTotal = AtomicLong(0)
    private val geckoPipelineVerticesTotal = AtomicLong(0)

    private val geckoPipelineBatchLe32Total = AtomicLong(0)
    private val geckoPipelineBatchLe64Total = AtomicLong(0)
    private val geckoPipelineBatchLe128Total = AtomicLong(0)
    private val geckoPipelineBatchLe256Total = AtomicLong(0)
    private val geckoPipelineBatchLe512Total = AtomicLong(0)
    private val geckoPipelineBatchLe1024Total = AtomicLong(0)
    private val geckoPipelineBatchGt1024Total = AtomicLong(0)

    private val geckoPipelineNanos = AtomicLong(0)
    private val geckoPipelineNanosTotal = AtomicLong(0)

    private val geckoQuadBulkNanos = AtomicLong(0)
    private val geckoQuadBulkNanosTotal = AtomicLong(0)
    private val geckoQuadLegacyNanos = AtomicLong(0)
    private val geckoQuadLegacyNanosTotal = AtomicLong(0)

    private val geckoQuadBulkVerticesTimed = AtomicLong(0)
    private val geckoQuadBulkVerticesTimedTotal = AtomicLong(0)
    private val geckoQuadLegacyVerticesTimed = AtomicLong(0)
    private val geckoQuadLegacyVerticesTimedTotal = AtomicLong(0)

    @Volatile
    private var geckoPipelineBackend: String = ""

    @Volatile
    private var geckoPipelineVectorized: Boolean = false

    private val textureBinds = AtomicLong(0)

    private val dispatcherPending = AtomicLong(0)
    private val renderManagerBuckets = AtomicLong(0)

    @Volatile
    private var lastFrame: Snapshot = Snapshot()

    internal fun resetForFrame() {
        if (!enabled) return
        drawCalls.set(0)
        vertices.set(0)
        batches.set(0)
        mergedBuffers.set(0)
        mergedBytes.set(0)
        mergeBuckets.set(0)
        mergeBucketTotalSize.set(0)
        mergeBucketMaxSize.set(0)
        vboUploads.set(0)
        vboUploadBytes.set(0)
        vboCacheHits.set(0)
        vboCacheMisses.set(0)

        opaqueChunkCacheHits.set(0)
        opaqueChunkCacheMisses.set(0)
        opaqueChunkCacheEvictions.set(0)
        opaqueChunkCacheUploads.set(0)
        opaqueChunkCacheUploadBytes.set(0)
        geckoQuads.set(0)
        geckoVertices.set(0)
        geckoBulkQuads.set(0)
        geckoFallbackQuads.set(0)
        geckoLegacyQuads.set(0)

        geckoPipelineBatches.set(0)
        geckoPipelineQuads.set(0)
        geckoPipelineVertices.set(0)

        geckoPipelineBatchLe32.set(0)
        geckoPipelineBatchLe64.set(0)
        geckoPipelineBatchLe128.set(0)
        geckoPipelineBatchLe256.set(0)
        geckoPipelineBatchLe512.set(0)
        geckoPipelineBatchLe1024.set(0)
        geckoPipelineBatchGt1024.set(0)

        geckoPipelineNanos.set(0)
        geckoQuadBulkNanos.set(0)
        geckoQuadLegacyNanos.set(0)
        geckoQuadBulkVerticesTimed.set(0)
        geckoQuadLegacyVerticesTimed.set(0)
        textureBinds.set(0)
        dispatcherPending.set(0)
        renderManagerBuckets.set(0)
    }

    internal fun snapshotFrame(): Snapshot {
        if (!enabled) {
            val snap = Snapshot()
            lastFrame = snap
            return snap
        }
        val snap = Snapshot(
            drawCalls = drawCalls.get(),
            vertices = vertices.get(),
            batches = batches.get(),
            mergedBuffers = mergedBuffers.get(),
            mergedBytes = mergedBytes.get(),
            mergeBuckets = mergeBuckets.get(),
            mergeBucketTotalSize = mergeBucketTotalSize.get(),
            mergeBucketMaxSize = mergeBucketMaxSize.get(),
            vboUploads = vboUploads.get(),
            vboUploadBytes = vboUploadBytes.get(),
            vboCacheHits = vboCacheHits.get(),
            vboCacheMisses = vboCacheMisses.get(),

            opaqueChunkCacheHits = opaqueChunkCacheHits.get(),
            opaqueChunkCacheMisses = opaqueChunkCacheMisses.get(),
            opaqueChunkCacheEvictions = opaqueChunkCacheEvictions.get(),
            opaqueChunkCacheUploads = opaqueChunkCacheUploads.get(),
            opaqueChunkCacheUploadBytes = opaqueChunkCacheUploadBytes.get(),
            geckoQuads = geckoQuads.get(),
            geckoVertices = geckoVertices.get(),
            geckoBulkQuads = geckoBulkQuads.get(),
            geckoFallbackQuads = geckoFallbackQuads.get(),
            geckoLegacyQuads = geckoLegacyQuads.get(),
            geckoPipelineBatches = geckoPipelineBatches.get(),
            geckoPipelineQuads = geckoPipelineQuads.get(),
            geckoPipelineVertices = geckoPipelineVertices.get(),

            geckoPipelineBatchLe32 = geckoPipelineBatchLe32.get(),
            geckoPipelineBatchLe64 = geckoPipelineBatchLe64.get(),
            geckoPipelineBatchLe128 = geckoPipelineBatchLe128.get(),
            geckoPipelineBatchLe256 = geckoPipelineBatchLe256.get(),
            geckoPipelineBatchLe512 = geckoPipelineBatchLe512.get(),
            geckoPipelineBatchLe1024 = geckoPipelineBatchLe1024.get(),
            geckoPipelineBatchGt1024 = geckoPipelineBatchGt1024.get(),
            geckoQuadsTotal = geckoQuadsTotal.get(),
            geckoVerticesTotal = geckoVerticesTotal.get(),
            geckoBulkQuadsTotal = geckoBulkQuadsTotal.get(),
            geckoFallbackQuadsTotal = geckoFallbackQuadsTotal.get(),
            geckoLegacyQuadsTotal = geckoLegacyQuadsTotal.get(),
            geckoPipelineBatchesTotal = geckoPipelineBatchesTotal.get(),
            geckoPipelineQuadsTotal = geckoPipelineQuadsTotal.get(),
            geckoPipelineVerticesTotal = geckoPipelineVerticesTotal.get(),

            geckoPipelineBatchLe32Total = geckoPipelineBatchLe32Total.get(),
            geckoPipelineBatchLe64Total = geckoPipelineBatchLe64Total.get(),
            geckoPipelineBatchLe128Total = geckoPipelineBatchLe128Total.get(),
            geckoPipelineBatchLe256Total = geckoPipelineBatchLe256Total.get(),
            geckoPipelineBatchLe512Total = geckoPipelineBatchLe512Total.get(),
            geckoPipelineBatchLe1024Total = geckoPipelineBatchLe1024Total.get(),
            geckoPipelineBatchGt1024Total = geckoPipelineBatchGt1024Total.get(),
            geckoPipelineBackend = geckoPipelineBackend,
            geckoPipelineVectorized = geckoPipelineVectorized,

            geckoPipelineNanos = geckoPipelineNanos.get(),
            geckoPipelineNanosTotal = geckoPipelineNanosTotal.get(),
            geckoQuadBulkNanos = geckoQuadBulkNanos.get(),
            geckoQuadBulkNanosTotal = geckoQuadBulkNanosTotal.get(),
            geckoQuadLegacyNanos = geckoQuadLegacyNanos.get(),
            geckoQuadLegacyNanosTotal = geckoQuadLegacyNanosTotal.get(),
            geckoQuadBulkVerticesTimed = geckoQuadBulkVerticesTimed.get(),
            geckoQuadBulkVerticesTimedTotal = geckoQuadBulkVerticesTimedTotal.get(),
            geckoQuadLegacyVerticesTimed = geckoQuadLegacyVerticesTimed.get(),
            geckoQuadLegacyVerticesTimedTotal = geckoQuadLegacyVerticesTimedTotal.get(),

            geckoCubeCacheHits = github.kasuminova.prototypemachinery.client.impl.render.gecko.BakedCubeCache.cacheHits,
            geckoCubeCacheMisses = github.kasuminova.prototypemachinery.client.impl.render.gecko.BakedCubeCache.cacheMisses,
            geckoCubeCacheSize = github.kasuminova.prototypemachinery.client.impl.render.gecko.BakedCubeCache.cacheSize,

            textureBinds = textureBinds.get(),
            dispatcherPending = dispatcherPending.get(),
            renderManagerBuckets = renderManagerBuckets.get(),
        )
        lastFrame = snap
        return snap
    }

    internal fun getLastFrame(): Snapshot = lastFrame

    internal fun addDraw(drawCallsInc: Int, verticesInc: Int) {
        if (!enabled) return
        if (drawCallsInc > 0) drawCalls.addAndGet(drawCallsInc.toLong())
        if (verticesInc > 0) vertices.addAndGet(verticesInc.toLong())
        batches.incrementAndGet()
    }

    internal fun addMerge(buffers: Int, bytes: Int) {
        if (!enabled) return
        if (buffers > 0) mergedBuffers.addAndGet(buffers.toLong())
        if (bytes > 0) mergedBytes.addAndGet(bytes.toLong())
    }

    internal fun noteMergeBucket(size: Int) {
        if (!enabled) return
        if (size <= 0) return
        mergeBuckets.incrementAndGet()
        mergeBucketTotalSize.addAndGet(size.toLong())

        // Update max (best-effort; races are fine for debug HUD).
        while (true) {
            val cur = mergeBucketMaxSize.get()
            if (size.toLong() <= cur) return
            if (mergeBucketMaxSize.compareAndSet(cur, size.toLong())) return
        }
    }

    internal fun addVboUpload(bytes: Int) {
        if (!enabled) return
        vboUploads.incrementAndGet()
        if (bytes > 0) vboUploadBytes.addAndGet(bytes.toLong())
    }

    internal fun addVboCacheHit() {
        if (!enabled) return
        vboCacheHits.incrementAndGet()
    }

    internal fun addVboCacheMiss() {
        if (!enabled) return
        vboCacheMisses.incrementAndGet()
    }

    internal fun addOpaqueChunkCacheHit() {
        if (!enabled) return
        opaqueChunkCacheHits.incrementAndGet()
    }

    internal fun addOpaqueChunkCacheMiss() {
        if (!enabled) return
        opaqueChunkCacheMisses.incrementAndGet()
    }

    internal fun addOpaqueChunkCacheEviction() {
        if (!enabled) return
        opaqueChunkCacheEvictions.incrementAndGet()
    }

    internal fun addOpaqueChunkCacheUpload(bytes: Int) {
        if (!enabled) return
        opaqueChunkCacheUploads.incrementAndGet()
        if (bytes > 0) opaqueChunkCacheUploadBytes.addAndGet(bytes.toLong())
    }

    internal fun addGeckoBulkQuad() {
        if (!enabled) return
        geckoQuads.incrementAndGet()
        geckoVertices.addAndGet(4)
        geckoBulkQuads.incrementAndGet()

        geckoQuadsTotal.incrementAndGet()
        geckoVerticesTotal.addAndGet(4)
        geckoBulkQuadsTotal.incrementAndGet()
    }

    internal fun addGeckoBulkQuads(quadCount: Int) {
        if (!enabled) return
        if (quadCount <= 0) return
        val q = quadCount.toLong()
        val v = q * 4L

        geckoQuads.addAndGet(q)
        geckoVertices.addAndGet(v)
        geckoBulkQuads.addAndGet(q)

        geckoQuadsTotal.addAndGet(q)
        geckoVerticesTotal.addAndGet(v)
        geckoBulkQuadsTotal.addAndGet(q)
    }

    internal fun addGeckoFallbackQuad(vertexCount: Int) {
        if (!enabled) return
        geckoQuads.incrementAndGet()
        if (vertexCount > 0) geckoVertices.addAndGet(vertexCount.toLong())
        geckoFallbackQuads.incrementAndGet()

        geckoQuadsTotal.incrementAndGet()
        if (vertexCount > 0) geckoVerticesTotal.addAndGet(vertexCount.toLong())
        geckoFallbackQuadsTotal.incrementAndGet()
    }

    internal fun addGeckoLegacyQuad(vertexCount: Int) {
        if (!enabled) return
        geckoQuads.incrementAndGet()
        if (vertexCount > 0) geckoVertices.addAndGet(vertexCount.toLong())
        geckoLegacyQuads.incrementAndGet()

        geckoQuadsTotal.incrementAndGet()
        if (vertexCount > 0) geckoVerticesTotal.addAndGet(vertexCount.toLong())
        geckoLegacyQuadsTotal.incrementAndGet()
    }

    internal fun addGeckoPipelineBatch(quadCount: Int, vertexCount: Int) {
        if (!enabled) return
        if (quadCount <= 0 || vertexCount <= 0) return

        // Still counts as Gecko work overall.
        geckoQuads.addAndGet(quadCount.toLong())
        geckoVertices.addAndGet(vertexCount.toLong())
        geckoBulkQuads.addAndGet(quadCount.toLong())

        geckoQuadsTotal.addAndGet(quadCount.toLong())
        geckoVerticesTotal.addAndGet(vertexCount.toLong())
        geckoBulkQuadsTotal.addAndGet(quadCount.toLong())

        // Pipeline-specific.
        geckoPipelineBatches.incrementAndGet()
        geckoPipelineQuads.addAndGet(quadCount.toLong())
        geckoPipelineVertices.addAndGet(vertexCount.toLong())

        when {
            vertexCount <= 32 -> geckoPipelineBatchLe32.incrementAndGet()
            vertexCount <= 64 -> geckoPipelineBatchLe64.incrementAndGet()
            vertexCount <= 128 -> geckoPipelineBatchLe128.incrementAndGet()
            vertexCount <= 256 -> geckoPipelineBatchLe256.incrementAndGet()
            vertexCount <= 512 -> geckoPipelineBatchLe512.incrementAndGet()
            vertexCount <= 1024 -> geckoPipelineBatchLe1024.incrementAndGet()
            else -> geckoPipelineBatchGt1024.incrementAndGet()
        }

        geckoPipelineBatchesTotal.incrementAndGet()
        geckoPipelineQuadsTotal.addAndGet(quadCount.toLong())
        geckoPipelineVerticesTotal.addAndGet(vertexCount.toLong())

        when {
            vertexCount <= 32 -> geckoPipelineBatchLe32Total.incrementAndGet()
            vertexCount <= 64 -> geckoPipelineBatchLe64Total.incrementAndGet()
            vertexCount <= 128 -> geckoPipelineBatchLe128Total.incrementAndGet()
            vertexCount <= 256 -> geckoPipelineBatchLe256Total.incrementAndGet()
            vertexCount <= 512 -> geckoPipelineBatchLe512Total.incrementAndGet()
            vertexCount <= 1024 -> geckoPipelineBatchLe1024Total.incrementAndGet()
            else -> geckoPipelineBatchGt1024Total.incrementAndGet()
        }
    }

    internal fun noteGeckoModernPipelineBackend(name: String, vectorized: Boolean) {
        if (name.isNotEmpty()) {
            geckoPipelineBackend = name
            geckoPipelineVectorized = vectorized
        }
    }

    internal fun addGeckoPipelineNanos(nanos: Long) {
        if (!enabled) return
        if (nanos <= 0L) return
        geckoPipelineNanos.addAndGet(nanos)
        geckoPipelineNanosTotal.addAndGet(nanos)
    }

    internal fun addGeckoQuadBulkNanos(nanos: Long, vertices: Int) {
        if (!enabled) return
        if (nanos <= 0L) return
        geckoQuadBulkNanos.addAndGet(nanos)
        geckoQuadBulkNanosTotal.addAndGet(nanos)
        if (vertices > 0) {
            geckoQuadBulkVerticesTimed.addAndGet(vertices.toLong())
            geckoQuadBulkVerticesTimedTotal.addAndGet(vertices.toLong())
        }
    }

    internal fun addGeckoQuadLegacyNanos(nanos: Long, vertices: Int) {
        if (!enabled) return
        if (nanos <= 0L) return
        geckoQuadLegacyNanos.addAndGet(nanos)
        geckoQuadLegacyNanosTotal.addAndGet(nanos)
        if (vertices > 0) {
            geckoQuadLegacyVerticesTimed.addAndGet(vertices.toLong())
            geckoQuadLegacyVerticesTimedTotal.addAndGet(vertices.toLong())
        }
    }

    internal fun addTextureBind() {
        if (!enabled) return
        textureBinds.incrementAndGet()
    }

    internal fun noteDispatcherPending(count: Int) {
        if (!enabled) return
        dispatcherPending.set(count.toLong())
    }

    internal fun noteRenderManagerBuckets(bucketCount: Int) {
        if (!enabled) return
        renderManagerBuckets.set(bucketCount.toLong())
    }
}
