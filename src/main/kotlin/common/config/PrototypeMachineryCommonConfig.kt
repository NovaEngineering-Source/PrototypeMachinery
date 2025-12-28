package github.kasuminova.prototypemachinery.common.config

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.tuning.OrientationToolTuning
import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.api.util.probability.ProbabilityTuning
import net.minecraftforge.common.config.Configuration
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent

/**
 * Simple Forge 1.12 config loader.
 *
 * We keep the tuning flags in API ([ProbabilityTuning]) and only update them from here,
 * so core utility code doesn't depend on Forge config classes.
 */
public object PrototypeMachineryCommonConfig {

    private const val CATEGORY_PROBABILITY = "probability"
    private const val CATEGORY_TOOLS = "tools"

    private const val CATEGORY_RENDER_ANIM = "render_animation"
    private const val CATEGORY_RENDER_MERGE = "render_merge"
    private const val CATEGORY_RENDER_VBO_CACHE = "render_vbo_cache"

    public fun load(event: FMLPreInitializationEvent) {
        val cfg = Configuration(event.suggestedConfigurationFile)
        try {
            cfg.load()

            ProbabilityTuning.enableExactSmallBinomial = cfg.getBoolean(
                /* name = */ "enableExactSmallBinomial",
                /* category = */ CATEGORY_PROBABILITY,
                /* defaultValue = */ false,
                /* comment = */ "If true, BinomialSampler uses exact Bernoulli trials when n <= ${ProbabilityTuning.exactSmallBinomialThreshold}. " +
                    "This improves distribution quality for small parallelism but costs O(n) RNG calls. " +
                    "Default false (scheme B: always use table-based approximation in hot paths).",
                /* languageKey = */ "${PrototypeMachinery.MOD_ID}.config.$CATEGORY_PROBABILITY.enableExactSmallBinomial"
            )

            // Controller orientation tool (wrench)
            OrientationToolTuning.maxDurability = cfg.getInt(
                /* name = */ "orientationToolMaxDurability",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.maxDurability,
                /* minValue = */ 1,
                /* maxValue = */ 100000,
                /* comment = */ "Max durability of the controller orientation tool (wrench)."
            )

            OrientationToolTuning.enchantability = cfg.getInt(
                /* name = */ "orientationToolEnchantability",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.enchantability,
                /* minValue = */ 0,
                /* maxValue = */ 100,
                /* comment = */ "Enchantability of the controller orientation tool (wrench)."
            )

            OrientationToolTuning.attackDamageEnabled = cfg.getFloat(
                /* name = */ "orientationToolAttackDamageEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.attackDamageEnabled.toFloat(),
                /* minValue = */ 0.0f,
                /* maxValue = */ 2048.0f,
                /* comment = */ "Attack damage when wrench is enabled (controller rotation mode)."
            ).toDouble()

            OrientationToolTuning.attackDamageDisabled = cfg.getFloat(
                /* name = */ "orientationToolAttackDamageDisabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.attackDamageDisabled.toFloat(),
                /* minValue = */ 0.0f,
                /* maxValue = */ 2048.0f,
                /* comment = */ "Attack damage when wrench is disabled (iron multi-tool mode)."
            ).toDouble()

            OrientationToolTuning.attackSpeed = cfg.getFloat(
                /* name = */ "orientationToolAttackSpeed",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.attackSpeed.toFloat(),
                /* minValue = */ -8.0f,
                /* maxValue = */ 8.0f,
                /* comment = */ "Attack speed modifier (vanilla style) for the wrench."
            ).toDouble()

            OrientationToolTuning.durabilityCostRotateEnabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostRotateEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostRotateEnabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per successful controller rotation when enabled."
            )

            OrientationToolTuning.durabilityCostAttackEnabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostAttackEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostAttackEnabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per attack hit when enabled."
            )

            OrientationToolTuning.durabilityCostAttackDisabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostAttackDisabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostAttackDisabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per attack hit when disabled."
            )

            OrientationToolTuning.durabilityCostBreakEnabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostBreakEnabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostBreakEnabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per block broken when enabled."
            )

            OrientationToolTuning.durabilityCostBreakDisabled = cfg.getInt(
                /* name = */ "orientationToolDurabilityCostBreakDisabled",
                /* category = */ CATEGORY_TOOLS,
                /* defaultValue = */ OrientationToolTuning.durabilityCostBreakDisabled,
                /* minValue = */ 0,
                /* maxValue = */ 1000,
                /* comment = */ "Durability cost per block broken when disabled."
            )

            // ---------------------
            // Client render tuning
            // ---------------------
            // These are safe to load on the server too (they just won't be used).

            RenderTuning.animSmooth = cfg.getBoolean(
                /* name = */ "animSmooth",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animSmooth,
                /* comment = */ "If true, drive Gecko animations with a render-frame-synced time key for smoother motion. " +
                    "This can increase background rebuild frequency."
            )

            RenderTuning.animStepTicks = cfg.getFloat(
                /* name = */ "animStepTicks",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animStepTicks.toFloat(),
                /* minValue = */ 0.01f,
                /* maxValue = */ 20.0f,
                /* comment = */ "Quantization step in ticks for animation time keys. " +
                    "1.0 => 20Hz (tick-rate), 0.5 => 40Hz, 0.25 => 80Hz. Smaller is smoother but can cost more CPU."
            ).toDouble()

            RenderTuning.animAutoThrottle = cfg.getBoolean(
                /* name = */ "animAutoThrottle",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animAutoThrottle,
                /* comment = */ "If true, automatically suppress smooth animation keys when build queue/stress is high (prevents runaway rebuild churn)."
            )

            RenderTuning.animMaxQueued = cfg.getInt(
                /* name = */ "animMaxQueued",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animMaxQueued.toInt(),
                /* minValue = */ 0,
                /* maxValue = */ Int.MAX_VALUE,
                /* comment = */ "If RenderTaskExecutor queued tasks exceed this, smooth animation is suppressed for that frame. 0 = disable when any backlog exists."
            ).toLong()

            RenderTuning.animMaxStressMultiplier = cfg.getInt(
                /* name = */ "animMaxStressMultiplier",
                /* category = */ CATEGORY_RENDER_ANIM,
                /* defaultValue = */ RenderTuning.animMaxStressMultiplier,
                /* minValue = */ 1,
                /* maxValue = */ 4096,
                /* comment = */ "If RenderStress.drawMultiplier exceeds this, smooth animation is suppressed (keeps stress tests meaningful)."
            )

            RenderTuning.mergeMinBuffers = cfg.getInt(
                /* name = */ "mergeMinBuffers",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeMinBuffers,
                /* minValue = */ 1,
                /* maxValue = */ 1024,
                /* comment = */ "Minimum buffers in a merge bucket before memcpy+merge is attempted. " +
                    "Increase to reduce merge overhead; decrease to reduce draw calls."
            )

            RenderTuning.mergeMinBytes = cfg.getInt(
                /* name = */ "mergeMinBytes",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeMinBytes,
                /* minValue = */ 0,
                /* maxValue = */ 256 * 1024 * 1024,
                /* comment = */ "Minimum total bytes in a merge bucket before memcpy+merge is attempted."
            )

            RenderTuning.mergeForceClientArrays = cfg.getBoolean(
                /* name = */ "mergeForceClientArrays",
                /* category = */ CATEGORY_RENDER_MERGE,
                /* defaultValue = */ RenderTuning.mergeForceClientArrays,
                /* comment = */ "If true, force client-side arrays for merged draws even when VBO is available. Useful for debugging driver stalls."
            )

            RenderTuning.vboCacheEnabled = cfg.getBoolean(
                /* name = */ "vboCacheEnabled",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCacheEnabled,
                /* comment = */ "If true, cache a GPU VBO per built BufferBuilder. This can dramatically reduce per-frame uploads on cache hits, " +
                    "at the cost of extra GPU memory (roughly mirrors CPU buffer sizes)."
            )

            RenderTuning.vboCachePreferIndividualAboveBytes = cfg.getInt(
                /* name = */ "vboCachePreferIndividualAboveBytes",
                /* category = */ CATEGORY_RENDER_VBO_CACHE,
                /* defaultValue = */ RenderTuning.vboCachePreferIndividualAboveBytes,
                /* minValue = */ 0,
                /* maxValue = */ 256 * 1024 * 1024,
                /* comment = */ "When a merge bucket's total bytes are >= this value, prefer drawing buffers individually using cached VBOs instead of merge+upload. 0 = always prefer individual when VBO cache is enabled."
            )

            RenderTuning.sanitize()
        } catch (t: Throwable) {
            // Don't fail startup on config issues.
            PrototypeMachinery.logger.warn("Failed to load config, using defaults.", t)
        } finally {
            if (cfg.hasChanged()) {
                cfg.save()
            }
        }
    }
}
