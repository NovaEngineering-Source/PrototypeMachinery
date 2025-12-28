package github.kasuminova.prototypemachinery.client.impl.render

import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight per-frame render stats for debugging/perf HUD.
 *
 * All counters are updated from the render thread, but we use atomics so they are safe to read from
 * the overlay event (also render thread) and resilient to accidental cross-thread reads.
 */
internal object RenderStats {

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

    private val textureBinds = AtomicLong(0)

    private val dispatcherPending = AtomicLong(0)
    private val renderManagerBuckets = AtomicLong(0)

    @Volatile
    private var lastFrame: Snapshot = Snapshot()

    internal fun resetForFrame() {
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
        textureBinds.set(0)
        dispatcherPending.set(0)
        renderManagerBuckets.set(0)
    }

    internal fun snapshotFrame(): Snapshot {
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
            textureBinds = textureBinds.get(),
            dispatcherPending = dispatcherPending.get(),
            renderManagerBuckets = renderManagerBuckets.get(),
        )
        lastFrame = snap
        return snap
    }

    internal fun getLastFrame(): Snapshot = lastFrame

    internal fun addDraw(drawCallsInc: Int, verticesInc: Int) {
        if (drawCallsInc > 0) drawCalls.addAndGet(drawCallsInc.toLong())
        if (verticesInc > 0) vertices.addAndGet(verticesInc.toLong())
        batches.incrementAndGet()
    }

    internal fun addMerge(buffers: Int, bytes: Int) {
        if (buffers > 0) mergedBuffers.addAndGet(buffers.toLong())
        if (bytes > 0) mergedBytes.addAndGet(bytes.toLong())
    }

    internal fun noteMergeBucket(size: Int) {
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
        vboUploads.incrementAndGet()
        if (bytes > 0) vboUploadBytes.addAndGet(bytes.toLong())
    }

    internal fun addTextureBind() {
        textureBinds.incrementAndGet()
    }

    internal fun noteDispatcherPending(count: Int) {
        dispatcherPending.set(count.toLong())
    }

    internal fun noteRenderManagerBuckets(bucketCount: Int) {
        renderManagerBuckets.set(bucketCount.toLong())
    }
}
