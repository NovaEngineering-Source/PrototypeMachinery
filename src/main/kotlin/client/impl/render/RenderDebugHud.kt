package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskExecutor
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

    @Volatile
    private var lastCacheFrameId: Int = -1

    @Volatile
    private var lastCacheStats: RenderTaskCache.StatsSnapshot? = null

    @SubscribeEvent
    fun onRenderTick(event: TickEvent.RenderTickEvent) {
        when (event.phase) {
            TickEvent.Phase.START -> {
                if (enabled) {
                    RenderStats.resetForFrame()
                }
            }

            TickEvent.Phase.END -> {
                if (enabled) {
                    RenderStats.snapshotFrame()
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

        val s = RenderStats.getLastFrame()
        val stress = RenderStress.drawMultiplier
        val native = NativeBufferStats.snapshot()
        val cacheSizes = RenderTaskCache.sizeSnapshot()
        val cacheStats = RenderTaskCache.statsSnapshot()
        val pool = RenderTaskExecutor.pool
        val frameId = RenderFrameClock.getFrameId()
        val frameTime = RenderFrameClock.getFrameTimeTicks()
        val animSmooth = RenderFrameClock.smoothAnimationsEnabled()
        val animKey = RenderFrameClock.currentAnimationTimeKey()
        val animStep = RenderFrameClock.animStepTicks()
        val animSuppress = RenderFrameClock.smoothSuppressedReason()

        val mergeBuckets = s.mergeBuckets
        val mergeAvgBucket = if (mergeBuckets > 0) (s.mergeBucketTotalSize.toDouble() / mergeBuckets.toDouble()) else 0.0

        val cacheDelta = computeCacheDelta(frameId, cacheStats)

        event.left.add("[PM] Render HUD")
        event.left.add("  stress.drawMultiplier=$stress")
        event.left.add(
            "  frame: id=$frameId  time=${String.format("%.3f", frameTime)}t  animSmooth=$animSmooth key=$animKey step=${String.format("%.3f", animStep)}t" +
                (if (animSuppress != null) " suppressed=$animSuppress" else "")
        )
        event.left.add("  dispatcher.pending=${s.dispatcherPending}")
        event.left.add("  renderManager.buckets=${s.renderManagerBuckets}")
        event.left.add("  draws=${s.drawCalls}  batches=${s.batches}  verts=${s.vertices}")
        if (stress > 1) {
            event.left.add("  note: draws/verts already include stress multiplier; GPU 100% is expected at high multipliers")
        }
        event.left.add("  merge: buffers=${s.mergedBuffers}  bytes=${formatBytes(s.mergedBytes)}")
        event.left.add(
            "        buckets=$mergeBuckets avg=${String.format("%.2f", mergeAvgBucket)} max=${s.mergeBucketMaxSize}"
        )
        event.left.add("  vbo: uploads=${s.vboUploads}  bytes=${formatBytes(s.vboUploadBytes)}")
        event.left.add("  textures: binds=${s.textureBinds}")

        event.left.add(
            "  tasks: cur=${cacheSizes.tasks} next=${cacheSizes.nextTasks}  calls=${cacheStats.getOrSubmitCalls} hitReady=${cacheStats.hitCurrentReady} hitBuild=${cacheStats.hitCurrentBuilding}"
        )
        event.left.add(
            "        subCur=${cacheStats.submittedCurrent} subNext=${cacheStats.submittedNext} promReady=${cacheStats.promotedNextReady} promCompat=${cacheStats.promotedNextCompatible} dropNext=${cacheStats.droppedNextDoneNotReady}"
        )
        if (cacheDelta != null) {
            event.left.add(
                "        delta: calls+${cacheDelta.getOrSubmitCalls} hitReady+${cacheDelta.hitCurrentReady} hitBuild+${cacheDelta.hitCurrentBuilding} subNext+${cacheDelta.submittedNext} prom+${cacheDelta.promotedNextReady + cacheDelta.promotedNextCompatible}"
            )
        }
        event.left.add(
            "  exec: parallelism=${pool.parallelism} active=${pool.activeThreadCount} running=${pool.runningThreadCount} queued=${pool.queuedTaskCount} steals=${pool.stealCount}"
        )

        event.left.add(
            "  bbPool: pooled=${BufferBuilderPool.pooledCount()} created=${BufferBuilderPool.createdCount()} held=${formatBytes(BufferBuilderPool.estimatedBytesHeld())}"
        )
        event.left.add(
            "  vboCache: enabled=${BufferBuilderVboCache.enabled()} entries=${BufferBuilderVboCache.size()} held=${formatBytes(BufferBuilderVboCache.bytesHeld())}"
        )
        event.left.add("  native: directAllocs=${native.directAllocations}  directBytes=${formatBytes(native.directAllocatedBytes)}")
        event.left.add(
            "          bufBuilders: req=${native.bufferBuilderRequests} ${formatBytes(native.bufferBuilderRequestBytes)}  new=${native.bufferBuilderNew} ${formatBytes(native.bufferBuilderNewBytes)}"
        )

        if (native.topBufferBuilderRequestTags.isNotEmpty()) {
            event.left.add("  native.top BufferBuilder (req):")
            for (t in native.topBufferBuilderRequestTags) {
                event.left.add("    ${t.tag}: allocs=${t.allocations} bytes=${formatBytes(t.bytes)}")
            }
        }
        if (native.topBufferBuilderNewTags.isNotEmpty()) {
            event.left.add("  native.top BufferBuilder (new):")
            for (t in native.topBufferBuilderNewTags) {
                event.left.add("    ${t.tag}: allocs=${t.allocations} bytes=${formatBytes(t.bytes)}")
            }
        }
        if (native.topDirectTags.isNotEmpty()) {
            event.left.add("  native.top Direct:")
            for (t in native.topDirectTags) {
                event.left.add("    ${t.tag}: allocs=${t.allocations} bytes=${formatBytes(t.bytes)}")
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "${bytes}B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1fKiB", kb)
        val mb = kb / 1024.0
        return String.format("%.2fMiB", mb)
    }

    private fun computeCacheDelta(
        frameId: Int,
        now: RenderTaskCache.StatsSnapshot,
    ): RenderTaskCache.StatsSnapshot? {
        val prev = lastCacheStats

        // Only advance once per frame.
        if (frameId != lastCacheFrameId) {
            lastCacheFrameId = frameId
            lastCacheStats = now
        }

        if (prev == null) return null

        fun d(a: Long, b: Long): Long = (a - b).coerceAtLeast(0L)

        return RenderTaskCache.StatsSnapshot(
            getOrSubmitCalls = d(now.getOrSubmitCalls, prev.getOrSubmitCalls),
            hitCurrentReady = d(now.hitCurrentReady, prev.hitCurrentReady),
            hitCurrentBuilding = d(now.hitCurrentBuilding, prev.hitCurrentBuilding),
            submittedCurrent = d(now.submittedCurrent, prev.submittedCurrent),
            submittedNext = d(now.submittedNext, prev.submittedNext),
            promotedNextReady = d(now.promotedNextReady, prev.promotedNextReady),
            promotedNextCompatible = d(now.promotedNextCompatible, prev.promotedNextCompatible),
            keptCurrentWhileNextBuilding = d(now.keptCurrentWhileNextBuilding, prev.keptCurrentWhileNextBuilding),
            droppedNextDoneNotReady = d(now.droppedNextDoneNotReady, prev.droppedNextDoneNotReady),
        )
    }
}
