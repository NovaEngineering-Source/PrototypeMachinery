package github.kasuminova.prototypemachinery.api.util.probability

/**
 * Runtime tuning switches for probability utilities.
 *
 * These values may be driven by mod config on startup, but are kept in API so that:
 * - core logic (e.g. [BinomialSampler]) does not depend on common/forge packages
 * - benchmarks can flip switches directly without loading the mod.
 */
public object ProbabilityTuning {

    /**
     * If true, [BinomialSampler] will use exact Bernoulli trials for small n.
     *
     * This preserves distribution quality for small parallelism, but costs O(n) RNG calls.
     *
     * Default: false (scheme B: always use table-based approximation where possible).
     */
    @Volatile
    public var enableExactSmallBinomial: Boolean = false

    /**
     * The cutoff for the "small n" exact branch.
     */
    public const val exactSmallBinomialThreshold: Int = 1024
}
