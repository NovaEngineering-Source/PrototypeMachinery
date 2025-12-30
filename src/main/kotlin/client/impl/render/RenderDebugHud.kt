package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache
import github.kasuminova.prototypemachinery.client.util.BufferBuilderPool
import github.kasuminova.prototypemachinery.client.util.BufferBuilderVboCache
import github.kasuminova.prototypemachinery.client.util.NativeBufferStats
import net.minecraft.client.Minecraft
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

/**
 * Debug HUD for PM render pipeline.
 *
 * Toggle via /pm_render_hud.
 */
internal object RenderDebugHud {

    @Volatile
    internal var enabled: Boolean = false

    // Smoothing for flickery per-frame counters (EMA over frames).
    private const val EMA_ALPHA: Double = 0.20

    // Throttle HUD text rebuild to reduce flicker/allocations.
    private const val HUD_TEXT_UPDATE_INTERVAL_MS: Long = 1000L

    private var lastHudTextUpdateMs: Long = 0L
    private val cachedLeftLines: MutableList<String> = ArrayList(16)

    private var wasEnabled: Boolean = false

    // For per-second deltas (cumulative counters -> window rates).
    private var lastNativeTotals: NativeBufferStats.Snapshot? = null
    private var lastTaskCacheTotals: RenderTaskCache.StatsSnapshot? = null

    // Track last-seen per-tag counters so we can estimate per-second rates for top tags.
    private val lastBbReqByTag: MutableMap<String, Pair<Long, Long>> = HashMap() // tag -> (allocs, bytes)
    private val lastBbNewByTag: MutableMap<String, Pair<Long, Long>> = HashMap() // tag -> (allocs, bytes)

    private object Window1s {
        var frames: Long = 0

        var drawCallsSum: Long = 0
        var batchesSum: Long = 0
        var verticesSum: Long = 0
        var texBindsSum: Long = 0
        var pendingSum: Long = 0

        var mergedBuffersSum: Long = 0
        var mergedBytesSum: Long = 0
        var mergeBucketsSum: Long = 0
        var mergeBucketTotalSizeSum: Long = 0
        var mergeBucketMaxSizeMax: Long = 0

        var vboUploadsSum: Long = 0
        var vboUploadBytesSum: Long = 0

        var opaqueChunkHitsSum: Long = 0
        var opaqueChunkMissesSum: Long = 0
        var opaqueChunkEvictionsSum: Long = 0
        var opaqueChunkUploadsSum: Long = 0
        var opaqueChunkUploadBytesSum: Long = 0

        var geckoQuadsSum: Long = 0
        var geckoVerticesSum: Long = 0

        // gperf window sums
        var pipeNanosSum: Long = 0
        var pipeVerticesSum: Long = 0

        // pipeline batch-size histogram sums (counts per 1s window)
        var pipeB32: Long = 0
        var pipeB64: Long = 0
        var pipeB128: Long = 0
        var pipeB256: Long = 0
        var pipeB512: Long = 0
        var pipeB1024: Long = 0
        var pipeBg1024: Long = 0

        var quadBulkNanosSum: Long = 0
        var quadBulkVerticesSum: Long = 0
        var quadLegacyNanosSum: Long = 0
        var quadLegacyVerticesSum: Long = 0

        fun reset() {
            frames = 0
            drawCallsSum = 0
            batchesSum = 0
            verticesSum = 0
            texBindsSum = 0
            pendingSum = 0

            mergedBuffersSum = 0
            mergedBytesSum = 0
            mergeBucketsSum = 0
            mergeBucketTotalSizeSum = 0
            mergeBucketMaxSizeMax = 0

            vboUploadsSum = 0
            vboUploadBytesSum = 0

            opaqueChunkHitsSum = 0
            opaqueChunkMissesSum = 0
            opaqueChunkEvictionsSum = 0
            opaqueChunkUploadsSum = 0
            opaqueChunkUploadBytesSum = 0

            geckoQuadsSum = 0
            geckoVerticesSum = 0

            pipeNanosSum = 0
            pipeVerticesSum = 0

            pipeB32 = 0
            pipeB64 = 0
            pipeB128 = 0
            pipeB256 = 0
            pipeB512 = 0
            pipeB1024 = 0
            pipeBg1024 = 0
            quadBulkNanosSum = 0
            quadBulkVerticesSum = 0
            quadLegacyNanosSum = 0
            quadLegacyVerticesSum = 0
        }

        fun addFrame(s: RenderStats.Snapshot) {
            frames++
            drawCallsSum += s.drawCalls
            batchesSum += s.batches
            verticesSum += s.vertices
            texBindsSum += s.textureBinds
            pendingSum += s.dispatcherPending

            mergedBuffersSum += s.mergedBuffers
            mergedBytesSum += s.mergedBytes

            mergeBucketsSum += s.mergeBuckets
            mergeBucketTotalSizeSum += s.mergeBucketTotalSize
            if (s.mergeBucketMaxSize > mergeBucketMaxSizeMax) mergeBucketMaxSizeMax = s.mergeBucketMaxSize

            vboUploadsSum += s.vboUploads
            vboUploadBytesSum += s.vboUploadBytes

            opaqueChunkHitsSum += s.opaqueChunkCacheHits
            opaqueChunkMissesSum += s.opaqueChunkCacheMisses
            opaqueChunkEvictionsSum += s.opaqueChunkCacheEvictions
            opaqueChunkUploadsSum += s.opaqueChunkCacheUploads
            opaqueChunkUploadBytesSum += s.opaqueChunkCacheUploadBytes

            geckoQuadsSum += s.geckoQuads
            geckoVerticesSum += s.geckoVertices

            if (s.geckoPipelineNanos > 0 && s.geckoPipelineVertices > 0) {
                pipeNanosSum += s.geckoPipelineNanos
                pipeVerticesSum += s.geckoPipelineVertices
            }

            // Histogram is useful even without timing.
            pipeB32 += s.geckoPipelineBatchLe32
            pipeB64 += s.geckoPipelineBatchLe64
            pipeB128 += s.geckoPipelineBatchLe128
            pipeB256 += s.geckoPipelineBatchLe256
            pipeB512 += s.geckoPipelineBatchLe512
            pipeB1024 += s.geckoPipelineBatchLe1024
            pipeBg1024 += s.geckoPipelineBatchGt1024
            if (s.geckoQuadBulkNanos > 0 && s.geckoQuadBulkVerticesTimed > 0) {
                quadBulkNanosSum += s.geckoQuadBulkNanos
                quadBulkVerticesSum += s.geckoQuadBulkVerticesTimed
            }
            if (s.geckoQuadLegacyNanos > 0 && s.geckoQuadLegacyVerticesTimed > 0) {
                quadLegacyNanosSum += s.geckoQuadLegacyNanos
                quadLegacyVerticesSum += s.geckoQuadLegacyVerticesTimed
            }
        }
    }

    private object Smooth {
        private var inited: Boolean = false

        var drawCalls: Double = 0.0
            private set
        var batches: Double = 0.0
            private set
        var vertices: Double = 0.0
            private set
        var texBinds: Double = 0.0
            private set
        var pending: Double = 0.0
            private set

        var mergedBuffers: Double = 0.0
            private set
        var mergedBytes: Double = 0.0
            private set
        var vboUploads: Double = 0.0
            private set
        var vboUploadBytes: Double = 0.0
            private set

        var geckoQuads: Double = 0.0
            private set
        var geckoVertices: Double = 0.0
            private set

        // Perf ns/v (already normalized), smoothed.
        var gperfPipeNsPerV: Double = Double.NaN
            private set
        var gperfQuadNsPerV: Double = Double.NaN
            private set

        fun reset() {
            inited = false
            drawCalls = 0.0
            batches = 0.0
            vertices = 0.0
            texBinds = 0.0
            pending = 0.0
            mergedBuffers = 0.0
            mergedBytes = 0.0
            vboUploads = 0.0
            vboUploadBytes = 0.0
            geckoQuads = 0.0
            geckoVertices = 0.0
            gperfPipeNsPerV = Double.NaN
            gperfQuadNsPerV = Double.NaN
        }

        fun update(s: RenderStats.Snapshot) {
            val had = inited
            val a = EMA_ALPHA

            fun ema(cur: Double, sample: Double): Double {
                return if (!had) sample else (cur + a * (sample - cur))
            }

            fun emaNaN(cur: Double, sample: Double): Double {
                if (sample.isNaN()) return cur
                if (!had || cur.isNaN()) return sample
                return cur + a * (sample - cur)
            }

            drawCalls = ema(drawCalls, s.drawCalls.toDouble())
            batches = ema(batches, s.batches.toDouble())
            vertices = ema(vertices, s.vertices.toDouble())
            texBinds = ema(texBinds, s.textureBinds.toDouble())
            pending = ema(pending, s.dispatcherPending.toDouble())

            mergedBuffers = ema(mergedBuffers, s.mergedBuffers.toDouble())
            mergedBytes = ema(mergedBytes, s.mergedBytes.toDouble())
            vboUploads = ema(vboUploads, s.vboUploads.toDouble())
            vboUploadBytes = ema(vboUploadBytes, s.vboUploadBytes.toDouble())

            geckoQuads = ema(geckoQuads, s.geckoQuads.toDouble())
            geckoVertices = ema(geckoVertices, s.geckoVertices.toDouble())

            val pipe = if (s.geckoPipelineNanos > 0 && s.geckoPipelineVertices > 0) {
                s.geckoPipelineNanos.toDouble() / s.geckoPipelineVertices.toDouble()
            } else {
                Double.NaN
            }

            val (qNs, qV) = if (s.geckoQuadBulkVerticesTimed > 0) {
                s.geckoQuadBulkNanos to s.geckoQuadBulkVerticesTimed
            } else {
                s.geckoQuadLegacyNanos to s.geckoQuadLegacyVerticesTimed
            }
            val quad = if (qNs > 0 && qV > 0) {
                qNs.toDouble() / qV.toDouble()
            } else {
                Double.NaN
            }

            gperfPipeNsPerV = emaNaN(gperfPipeNsPerV, pipe)
            gperfQuadNsPerV = emaNaN(gperfQuadNsPerV, quad)

            inited = true
        }
    }

    @SubscribeEvent
    fun onRenderTick(event: TickEvent.RenderTickEvent) {
        when (event.phase) {
            TickEvent.Phase.START -> {
                if (enabled) {
                    RenderStats.enabled = true
                    RenderStats.resetForFrame()

                    if (!wasEnabled) {
                        Smooth.reset()
                        Window1s.reset()
                        lastNativeTotals = null
                        lastTaskCacheTotals = null
                        lastBbReqByTag.clear()
                        lastBbNewByTag.clear()
                        // Force a quick first update.
                        lastHudTextUpdateMs = 0L
                    }
                } else {
                    RenderStats.enabled = false
                }

                wasEnabled = enabled
            }

            TickEvent.Phase.END -> {
                if (enabled) {
                    val s = RenderStats.snapshotFrame()
                    Smooth.update(s)

                    // Collect window samples every frame.
                    Window1s.addFrame(s)

                    // Rebuild HUD text at most once per second.
                    val nowMs = net.minecraft.client.Minecraft.getSystemTime()
                    if (lastHudTextUpdateMs == 0L || (nowMs - lastHudTextUpdateMs) >= HUD_TEXT_UPDATE_INTERVAL_MS) {
                        val prevMs = lastHudTextUpdateMs
                        lastHudTextUpdateMs = nowMs
                        rebuildCachedLines(s, prevMs = prevMs, nowMs = nowMs)
                        Window1s.reset()
                    }
                }
            }
        }
    }

    @SubscribeEvent
    fun onOverlayText(event: RenderGameOverlayEvent.Text) {
        if (!enabled) return

        val mc = Minecraft.getMinecraft()
        if (mc.gameSettings.showDebugInfo) {
            // Avoid fighting with F3; keep this HUD for normal gameplay profiling.
            return
        }

        // Display text is throttled to reduce flicker; onOverlayText only copies cached lines.
        val lines = cachedLeftLines
        var i = 0
        val n = lines.size
        while (i < n) {
            event.left.add(lines[i])
            i++
        }
    }

    private fun rebuildCachedLines(last: RenderStats.Snapshot, prevMs: Long, nowMs: Long) {
        cachedLeftLines.clear()

        val stress = RenderStress.drawMultiplier
        val native = NativeBufferStats.snapshot()
        val taskCache = RenderTaskCache.statsSnapshot()
        val chunkCache = MachineRenderDispatcher.opaqueChunkCacheStats()

        // Animation key status (sampled once per HUD refresh).
        val animKey = RenderFrameClock.currentAnimationTimeKey()
        val animSupp = RenderFrameClock.smoothSuppressedReason()

        val dtMs = if (prevMs == 0L) HUD_TEXT_UPDATE_INTERVAL_MS else (nowMs - prevMs).coerceAtLeast(1L)
        val invDt = 1000.0 / dtMs.toDouble()

        fun perSec(delta: Long): Long {
            return (delta.toDouble() * invDt).toLong()
        }

        // Compute deltas from last sample.
        val nativePrev = lastNativeTotals
        val taskPrev = lastTaskCacheTotals
        lastNativeTotals = native
        lastTaskCacheTotals = taskCache

        val bbReqPerSec = if (nativePrev != null) perSec(native.bufferBuilderRequests - nativePrev.bufferBuilderRequests) else 0L
        val bbNewPerSec = if (nativePrev != null) perSec(native.bufferBuilderNew - nativePrev.bufferBuilderNew) else 0L
        val bbReqBytesPerSec = if (nativePrev != null) perSec(native.bufferBuilderRequestBytes - nativePrev.bufferBuilderRequestBytes) else 0L
        val bbNewBytesPerSec = if (nativePrev != null) perSec(native.bufferBuilderNewBytes - nativePrev.bufferBuilderNewBytes) else 0L

        val taskGetPerSec = if (taskPrev != null) perSec(taskCache.getOrSubmitCalls - taskPrev.getOrSubmitCalls) else 0L
        val taskHitReadyPerSec = if (taskPrev != null) perSec(taskCache.hitCurrentReady - taskPrev.hitCurrentReady) else 0L
        val taskHitBuildPerSec = if (taskPrev != null) perSec(taskCache.hitCurrentBuilding - taskPrev.hitCurrentBuilding) else 0L
        val taskSubCurPerSec = if (taskPrev != null) perSec(taskCache.submittedCurrent - taskPrev.submittedCurrent) else 0L
        val taskSubNextPerSec = if (taskPrev != null) perSec(taskCache.submittedNext - taskPrev.submittedNext) else 0L

        val frames = Window1s.frames
        val invFrames = if (frames > 0) (1.0 / frames.toDouble()) else 0.0

        fun avgPerFrame(sum: Long): Long {
            if (frames <= 0) return 0L
            return (sum.toDouble() * invFrames).toLong()
        }

        val drawCallsAvg = avgPerFrame(Window1s.drawCallsSum)
        val batchesAvg = avgPerFrame(Window1s.batchesSum)
        val verticesAvg = avgPerFrame(Window1s.verticesSum)
        val texBindsAvg = avgPerFrame(Window1s.texBindsSum)
        val pendingAvg = avgPerFrame(Window1s.pendingSum)

        val mergedBuffersAvg = avgPerFrame(Window1s.mergedBuffersSum)
        val mergedBytesAvg = avgPerFrame(Window1s.mergedBytesSum)
        val vboUploadsAvg = avgPerFrame(Window1s.vboUploadsSum)
        val vboUploadBytesAvg = avgPerFrame(Window1s.vboUploadBytesSum)

        val chunkUploadsAvg = avgPerFrame(Window1s.opaqueChunkUploadsSum)
        val chunkUploadBytesAvg = avgPerFrame(Window1s.opaqueChunkUploadBytesSum)

        val mergeBuckets = Window1s.mergeBucketsSum
        val mergeAvgBucket = if (mergeBuckets > 0) (Window1s.mergeBucketTotalSizeSum.toDouble() / mergeBuckets.toDouble()) else 0.0
        val mergeMaxBucket = Window1s.mergeBucketMaxSizeMax

        cachedLeftLines.add("[PM] Render HUD  stress=$stress")
        cachedLeftLines.add(
            "  anim: smooth=${RenderTuning.animSmooth} key=$animKey step=${String.format("%.2f", RenderTuning.animStepTicks)} auto=${RenderTuning.animAutoThrottle} maxStress=${RenderTuning.animMaxStressMultiplier} supp=${animSupp ?: "-"}"
        )
        cachedLeftLines.add(
            "  draws~=$drawCallsAvg  batches~=$batchesAvg  verts~=$verticesAvg  tex~=$texBindsAvg  pend~=$pendingAvg"
        )

        // Task/build verification lines: if static model is stable, submittedNext/s and bbNew/s should trend to ~0.
        cachedLeftLines.add(
            "  tasks/s: get~=$taskGetPerSec hitR~=$taskHitReadyPerSec hitB~=$taskHitBuildPerSec subC~=$taskSubCurPerSec subN~=$taskSubNextPerSec"
        )
        cachedLeftLines.add(
            "  bb/s: req~=$bbReqPerSec new~=$bbNewPerSec reqB~=${formatBytes(bbReqBytesPerSec)} newB~=${formatBytes(bbNewBytesPerSec)}"
        )

        // Top tag breakdown (best-effort): helps pinpoint which build task/pass is driving BufferBuilder borrows.
        // We only compute deltas for tags that appear in the current top list.
        run {
            if (nativePrev != null && native.topBufferBuilderRequestTags.isNotEmpty()) {
                val parts = ArrayList<String>(native.topBufferBuilderRequestTags.size)
                for (t in native.topBufferBuilderRequestTags) {
                    val prev = lastBbReqByTag[t.tag]
                    val dA = if (prev != null) (t.allocations - prev.first) else 0L
                    val dB = if (prev != null) (t.bytes - prev.second) else 0L
                    lastBbReqByTag[t.tag] = t.allocations to t.bytes

                    val aPerS = perSec(dA)
                    val bPerS = perSec(dB)
                    if (bPerS > 0 || aPerS > 0) {
                        parts.add("${t.tag}=${formatBytes(bPerS)}")
                    }
                }
                if (parts.isNotEmpty()) {
                    cachedLeftLines.add("  bbReqTop/s: ${parts.joinToString(" | ")}")
                }
            } else {
                // Seed baseline
                for (t in native.topBufferBuilderRequestTags) {
                    lastBbReqByTag[t.tag] = t.allocations to t.bytes
                }
            }

            if (nativePrev != null && native.topBufferBuilderNewTags.isNotEmpty()) {
                val parts = ArrayList<String>(native.topBufferBuilderNewTags.size)
                for (t in native.topBufferBuilderNewTags) {
                    val prev = lastBbNewByTag[t.tag]
                    val dA = if (prev != null) (t.allocations - prev.first) else 0L
                    val dB = if (prev != null) (t.bytes - prev.second) else 0L
                    lastBbNewByTag[t.tag] = t.allocations to t.bytes

                    val aPerS = perSec(dA)
                    val bPerS = perSec(dB)
                    if (bPerS > 0 || aPerS > 0) {
                        parts.add("${t.tag}=${formatBytes(bPerS)}")
                    }
                }
                if (parts.isNotEmpty()) {
                    cachedLeftLines.add("  bbNewTop/s: ${parts.joinToString(" | ")}")
                }
            } else {
                // Seed baseline
                for (t in native.topBufferBuilderNewTags) {
                    lastBbNewByTag[t.tag] = t.allocations to t.bytes
                }
            }
        }

        if (Window1s.mergedBuffersSum > 0 || Window1s.mergedBytesSum > 0 || mergeBuckets > 0) {
            cachedLeftLines.add(
                "  merge: bufs~=$mergedBuffersAvg bytes~=${formatBytes(mergedBytesAvg)} buckets=$mergeBuckets avg=${String.format("%.2f", mergeAvgBucket)} max=$mergeMaxBucket"
            )
        }

        cachedLeftLines.add(
            "  vbo: up~=$vboUploadsAvg bytes~=${formatBytes(vboUploadBytesAvg)} cache(h=${last.vboCacheHits} m=${last.vboCacheMisses})"
        )

        val showChunk = chunkCache.size > 0 || Window1s.opaqueChunkUploadsSum > 0 || Window1s.opaqueChunkHitsSum > 0 || Window1s.opaqueChunkMissesSum > 0
        if (showChunk) {
            cachedLeftLines.add(
                "  chunkVbo: up~=$chunkUploadsAvg bytes~=${formatBytes(chunkUploadBytesAvg)} h=${last.opaqueChunkCacheHits} m=${last.opaqueChunkCacheMisses} ev=${last.opaqueChunkCacheEvictions} size=${chunkCache.size} mem=${formatBytes(chunkCache.bytesHeld)}"
            )
        }

        if (last.geckoQuadsTotal > 0 || last.geckoVerticesTotal > 0) {
            val framePart = if (Window1s.geckoQuadsSum > 0 || Window1s.geckoVerticesSum > 0) {
                val fq = avgPerFrame(Window1s.geckoQuadsSum)
                val fv = avgPerFrame(Window1s.geckoVerticesSum)
                " f~=$fq/$fv"
            } else {
                ""
            }

            cachedLeftLines.add(
                "  gecko: t=${last.geckoQuadsTotal}/${last.geckoVerticesTotal}$framePart b=${last.geckoBulkQuadsTotal} fb=${last.geckoFallbackQuadsTotal} l=${last.geckoLegacyQuadsTotal}"
            )

            if (last.geckoPipelineBatchesTotal > 0 || last.geckoPipelineBackend.isNotEmpty()) {
                val bk = if (last.geckoPipelineBackend.isNotEmpty()) {
                    val tag = if (last.geckoPipelineVectorized) "v" else "s"
                    " ${shortBackendName(last.geckoPipelineBackend)}($tag)"
                } else {
                    ""
                }
                cachedLeftLines.add(
                    "  gpipe: b=${last.geckoPipelineBatchesTotal} q=${last.geckoPipelineQuadsTotal} v=${last.geckoPipelineVerticesTotal}$bk"
                )

                val anyBins = (
                    Window1s.pipeB32 or Window1s.pipeB64 or Window1s.pipeB128 or
                        Window1s.pipeB256 or Window1s.pipeB512 or Window1s.pipeB1024 or Window1s.pipeBg1024
                    ) != 0L
                if (anyBins) {
                    cachedLeftLines.add(
                        "  gbin: 32=${Window1s.pipeB32} 64=${Window1s.pipeB64} 128=${Window1s.pipeB128} 256=${Window1s.pipeB256} 512=${Window1s.pipeB512} 1k=${Window1s.pipeB1024} +${Window1s.pipeBg1024}"
                    )
                }
            }

            // gperf: use 1s window sums to avoid per-frame flicker.
            val pipeNsPerV = if (Window1s.pipeNanosSum > 0 && Window1s.pipeVerticesSum > 0) {
                Window1s.pipeNanosSum.toDouble() / Window1s.pipeVerticesSum.toDouble()
            } else {
                Double.NaN
            }

            val quadNsPerV = when {
                Window1s.quadBulkNanosSum > 0 && Window1s.quadBulkVerticesSum > 0 ->
                    Window1s.quadBulkNanosSum.toDouble() / Window1s.quadBulkVerticesSum.toDouble()

                Window1s.quadLegacyNanosSum > 0 && Window1s.quadLegacyVerticesSum > 0 ->
                    Window1s.quadLegacyNanosSum.toDouble() / Window1s.quadLegacyVerticesSum.toDouble()

                else -> Double.NaN
            }

            if (!pipeNsPerV.isNaN() || !quadNsPerV.isNaN()) {
                val pipe = if (!pipeNsPerV.isNaN()) "p~=${formatNsPerVertexValue(pipeNsPerV)}" else "p~=na"
                val quad = if (!quadNsPerV.isNaN()) "q~=${formatNsPerVertexValue(quadNsPerV)}" else "q~=na"
                cachedLeftLines.add("  gperf: $pipe $quad")
            }

            // Cube pre-bake cache stats
            if (last.geckoCubeCacheSize > 0 || last.geckoCubeCacheHits > 0 || last.geckoCubeCacheMisses > 0) {
                cachedLeftLines.add(
                    "  gcache: size=${last.geckoCubeCacheSize} h=${last.geckoCubeCacheHits} m=${last.geckoCubeCacheMisses}"
                )
            }
        }

        cachedLeftLines.add(
            "  mem: bbPool=${formatBytes(BufferBuilderPool.estimatedBytesHeld())} bbPooled=${formatBytes(BufferBuilderPool.pooledBytesHeld())} bbBig=${formatBytes(BufferBuilderPool.oversizeBytesHeld())}(${BufferBuilderPool.oversizeCount()} d=${BufferBuilderPool.oversizeDroppedCount()}/${formatBytes(BufferBuilderPool.oversizeDroppedBytes())}) vboCache=${formatBytes(BufferBuilderVboCache.bytesHeld())} chunkVbo=${formatBytes(chunkCache.bytesHeld)}(${chunkCache.size}) direct=${formatBytes(native.directAllocatedBytes)}"
        )
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1fKiB", kb)
        val mb = kb / 1024.0
        return String.format("%.2fMiB", mb)
    }

    private fun shortBackendName(full: String): String {
        if (full.isEmpty()) return ""
        // Prefer last segment after '.'; then clamp length to avoid HUD spam.
        val last = full.substringAfterLast('.')
        return if (last.length <= 16) last else last.substring(0, 16)
    }

    private fun formatNsPerVertex(nanos: Long, vertices: Long): String {
        if (nanos <= 0L || vertices <= 0L) return "na"
        val v = nanos.toDouble() / vertices.toDouble()
        return if (v >= 1000.0) {
            String.format("%.2fus/v", v / 1000.0)
        } else {
            String.format("%.0fns/v", v)
        }
    }

    private fun formatNsPerVertexValue(nsPerV: Double): String {
        if (nsPerV.isNaN() || nsPerV <= 0.0) return "na"
        return if (nsPerV >= 1000.0) {
            String.format("%.2fus/v", nsPerV / 1000.0)
        } else {
            String.format("%.0fns/v", nsPerV)
        }
    }

}
