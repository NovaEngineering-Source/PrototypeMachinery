package github.kasuminova.prototypemachinery.common.config

import github.kasuminova.prototypemachinery.PrototypeMachinery
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
