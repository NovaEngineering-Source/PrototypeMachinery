package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.impl.render.RenderFrameClock.frameTimeTicks
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskExecutor
import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.floor

/**
 * Captures a consistent per-frame time snapshot for render-synced effects.
 *
 * Why:
 * - We often render the same model in multiple phases (TESR batch, post-batch flush, bloom).
 * - If each phase samples time independently, animations can appear offset between passes.
 *
 * This object snapshots a single [frameTimeTicks] at RenderTick START and exposes a quantized
 * [animationTimeKey] for driving rebuild cadence.
 */
internal object RenderFrameClock {

    /**
     * NOTE: All tuning values are driven by [RenderTuning] (loaded from Forge config).
     * We intentionally do not read JVM properties here so players can tweak without launcher args.
     */

    @Volatile
    private var frameId: Int = 0

    @Volatile
    private var frameTimeTicks: Double = 0.0

    @Volatile
    private var smoothSuppressedReason: String? = null

    @SubscribeEvent
    fun onRenderTick(event: TickEvent.RenderTickEvent) {
        if (event.phase != TickEvent.Phase.START) return

        val mc = Minecraft.getMinecraft()
        val world = mc.world

        // In menus/world-less states, keep monotonic id but use 0 time.
        if (world == null) {
            frameId++
            frameTimeTicks = 0.0
            return
        }

        // Forge provides the per-frame partial ticks here; avoid accessing Minecraft.timer (private in some mappings).
        val partial = runCatching {
            // RenderTickEvent has renderTickTime in 1.12.x
            @Suppress("DEPRECATION")
            event.renderTickTime.toDouble()
        }.getOrDefault(0.0)
        val t = world.totalWorldTime.toDouble() + partial

        frameId++
        frameTimeTicks = t
    }

    internal fun getFrameId(): Int = frameId

    internal fun getFrameTimeTicks(): Double = frameTimeTicks

    internal fun smoothAnimationsEnabled(): Boolean = RenderTuning.animSmooth

    internal fun smoothSuppressedReason(): String? = smoothSuppressedReason

    internal fun animStepTicks(): Double = RenderTuning.animStepTicks

    internal fun currentAnimationTimeKey(): Int {
        smoothSuppressedReason = null
        if (!RenderTuning.animSmooth) return 0

        val t = frameTimeTicks
        if (t <= 0.0) return 0
        val step = RenderTuning.animStepTicks

        // Base key at configured resolution.
        val raw = floor(t / step).toLong().toInt()

        if (!RenderTuning.animAutoThrottle) {
            return raw
        }

        // Auto-throttle: instead of returning 0 (which freezes animation), snap to a coarser grid.
        // IMPORTANT: We must keep the mapping reversible by seekTimeTicksFromKey(key) = key * animStepTicks,
        // so we only return a *multiple* of the base key.
        var snap = 1
        val reasons = ArrayList<String>(2)

        // Stress testing intentionally amplifies draw work; using smooth animation keys here
        // can create massive rebuild churn and makes profiling misleading.
        val maxStress = RenderTuning.animMaxStressMultiplier
        val stress = RenderStress.drawMultiplier
        if (stress > maxStress) {
            // e.g. stress=8, max=2 => snap>=4
            val s = ((stress + maxStress - 1) / maxStress).coerceAtLeast(2)
            snap = maxOf(snap, s)
            reasons += "stress"
        }

        // Queue-based throttle: animMaxQueued <= 0 means "disable queue throttle".
        val maxQueued = RenderTuning.animMaxQueued
        if (maxQueued > 0L) {
                val queued = RenderTaskExecutor.queuedBuildTaskCount()
            if (queued > maxQueued) {
                // Use ceil(queued/maxQueued) as snap factor.
                val q = ((queued + maxQueued - 1L) / maxQueued).coerceAtLeast(2L)
                snap = maxOf(snap, q.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                reasons += "queue"
            }
        }

        if (snap <= 1) {
            return raw
        }

        // Cap snap so animation still makes visible progress eventually.
        snap = snap.coerceIn(2, 64)
        smoothSuppressedReason = reasons.joinToString("+") + " x$snap"

        val snapped = raw - (raw % snap)

        // Int wrap is acceptable for extremely long uptimes; it keeps key monotonic enough
        // for practical play sessions while preserving a simple reversible mapping.
        return snapped
    }

    internal fun seekTimeTicksFromKey(animationTimeKey: Int): Double {
        if (animationTimeKey == 0) return 0.0
        return animationTimeKey.toDouble() * RenderTuning.animStepTicks
    }
}
