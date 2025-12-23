package github.kasuminova.prototypemachinery.api.util.probability

import github.kasuminova.prototypemachinery.api.util.probability.ChanceMath.maxTimes
import github.kasuminova.prototypemachinery.api.util.probability.ChanceMath.sampleTimes
import java.util.Random
import kotlin.math.floor

/**
 * Utilities for converting a chance percentage to an execution count under parallelism.
 *
 * This is designed to be:
 * - deterministic given the same [Random]
 * - compatible with chance > 100% ("may happen multiple times")
 * - stable under parallelism (avoids all-or-nothing extremes)
 */
public object ChanceMath {

    /**
     * Computes the number of times an operation should execute.
     *
     * Given:
     * - [parallels] = k
     * - [chancePercent] = C (may be > 100)
     *
     * Let c = C / 100.
     * c = g + p, where g = floor(c), p in [0,1).
     *
     * times = g*k + Binomial(k, p)
     */
    public fun sampleTimes(random: Random, parallels: Int, chancePercent: Double): Long {
        if (parallels <= 0) return 0L
        if (!chancePercent.isFinite()) return 0L
        if (chancePercent <= 0.0) return 0L

        val c = chancePercent / 100.0
        val g = floor(c).toLong().coerceAtLeast(0L)
        val p = (c - g.toDouble()).coerceIn(0.0, 1.0)

        val extra = if (p <= 0.0) 0L else BinomialSampler.sample(random, parallels, p)

        // Guard against overflow (extremely large chance). In practice should never happen.
        val base = safeMul(g, parallels.toLong())
        return safeAdd(base, extra)
    }

    /**
     * Upper bound of [sampleTimes] for capacity checking.
     *
     * maxTimes = ceil(c) * k
     *
     * This is conservative: it ensures that if you can satisfy/fit [maxTimes],
     * then any sampled result is also satisfiable.
     */
    public fun maxTimes(parallels: Int, chancePercent: Double): Long {
        if (parallels <= 0) return 0L
        if (!chancePercent.isFinite()) return 0L
        if (chancePercent <= 0.0) return 0L

        val c = chancePercent / 100.0
        val g = floor(c).toLong().coerceAtLeast(0L)
        val p = (c - g.toDouble()).coerceIn(0.0, 1.0)
        val ceil = if (p > 0.0) g + 1L else g
        return safeMul(ceil, parallels.toLong())
    }

    private fun safeAdd(a: Long, b: Long): Long {
        val r = a + b
        // overflow detection
        if (((a xor r) and (b xor r)) < 0) {
            return Long.MAX_VALUE
        }
        return r
    }

    private fun safeMul(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        val r = a * b
        if (r / b != a) return Long.MAX_VALUE
        return r
    }
}
