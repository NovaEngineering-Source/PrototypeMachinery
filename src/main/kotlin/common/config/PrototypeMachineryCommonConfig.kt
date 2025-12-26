package github.kasuminova.prototypemachinery.common.config

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.tuning.OrientationToolTuning
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
