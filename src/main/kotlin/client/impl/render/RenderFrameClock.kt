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

        if (RenderTuning.animAutoThrottle) {
            // Stress testing intentionally amplifies draw work; using smooth animation keys here
            // can create massive rebuild churn and makes profiling misleading.
            if (RenderStress.drawMultiplier > RenderTuning.animMaxStressMultiplier) {
                smoothSuppressedReason = "stress"
                return 0
            }
            val queued = runCatching { RenderTaskExecutor.pool.queuedTaskCount }.getOrDefault(0L)
            if (queued > RenderTuning.animMaxQueued) {
                smoothSuppressedReason = "queue"
                return 0
            }
        }
        val t = frameTimeTicks
        if (t <= 0.0) return 0
        val step = RenderTuning.animStepTicks
        val q = floor(t / step).toLong()
        // Int wrap is acceptable for extremely long uptimes; it keeps key monotonic enough
        // for practical play sessions while preserving a simple reversible mapping.
        return q.toInt()
    }

    internal fun seekTimeTicksFromKey(animationTimeKey: Int): Double {
        if (animationTimeKey == 0) return 0.0
        return animationTimeKey.toDouble() * RenderTuning.animStepTicks
    }
}
