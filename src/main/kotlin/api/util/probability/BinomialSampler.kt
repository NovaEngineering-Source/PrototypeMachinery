package github.kasuminova.prototypemachinery.api.util.probability

import java.util.Random
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Binomial(n, p) sampler.
 *
 * Goal:
 * - expected runtime close to O(1) for large n
 * - deterministic given the same [Random]
 *
 * Notes:
 * - For small n, we use direct Bernoulli trials (exact).
 * - For very small mean, we use a Poisson approximation. This is expected O(mean), but we only
 *   use it under a fixed mean threshold, so the expected cost is still effectively O(1).
 * - Otherwise we use a normal approximation via [Random.nextGaussian] (fast, expected O(1)).
 *
 * This is intended for gameplay/procedural randomness where large parallelism values may occur.
 * If you require an exact binomial sampler for all ranges, we can add a more complex rejection sampler later.
 */
public object BinomialSampler {

    // Power-of-two sized Gaussian table to avoid Random.nextGaussian() in hot paths.
    // NOTE: the table is generated once at class init time (constant overhead).
    private const val GAUSS_TABLE_SIZE: Int = 2048
    private const val GAUSS_TABLE_MASK: Int = GAUSS_TABLE_SIZE - 1

    private val GAUSS_TABLE: DoubleArray = DoubleArray(GAUSS_TABLE_SIZE).also { table ->
        // Fixed seed for determinism across JVM runs.
        val r = Random(0x4A4D4831L) // "JMH1"
        for (i in table.indices) {
            table[i] = r.nextGaussian()
        }
    }

    /**
     * Sample X ~ Binomial(n, p).
     */
    public fun sample(random: Random, n: Int, p: Double): Long {
        if (n <= 0) return 0L
        if (!p.isFinite()) return 0L
        if (p <= 0.0) return 0L
        if (p >= 1.0) return n.toLong()

        // Use symmetry to keep p <= 0.5
        if (p > 0.5) {
            val x = sample(random, n, 1.0 - p)
            return (n.toLong() - x).coerceIn(0L, n.toLong())
        }

        // Scheme A (optional): exact for small n.
        // Default is scheme B: avoid O(n) loops and use the table-based normal approximation instead.
        if (ProbabilityTuning.enableExactSmallBinomial && n <= ProbabilityTuning.exactSmallBinomialThreshold) {
            return sampleExactBernoulli(random, n, p)
        }

        val mean = n.toDouble() * p

        // Poisson approximation for tiny mean (good when p is small).
        // Expected iterations ~ mean, so still ~O(1) when mean is bounded.
        if (mean < 16.0) {
            return samplePoissonKnuth(random, mean).coerceIn(0L, n.toLong())
        }

        return sampleNormalTableInternal(random, n, p, mean)
    }

    private fun sampleNormalTableInternal(random: Random, n: Int, p: Double, mean: Double): Long {
        // Normal approximation for the general case, avoiding Random.nextGaussian.
        val variance = mean * (1.0 - p)
        if (variance <= 0.0) {
            return floor(mean + 0.5).toLong().coerceIn(0L, n.toLong())
        }
        val sd = sqrt(variance)

        // Single table lookup + clamp: minimal RNG cost.
        val idx = random.nextInt() and GAUSS_TABLE_MASK
        val z = GAUSS_TABLE[idx]
        val x = floor(mean + sd * z + 0.5).toLong()
        return x.coerceIn(0L, n.toLong())
    }

    private fun sampleExactBernoulli(random: Random, n: Int, p: Double): Long {
        var c = 0L
        for (i in 0 until n) {
            if (random.nextDouble() < p) c++
        }
        return c
    }

    private fun samplePoissonKnuth(random: Random, lambda: Double): Long {
        if (!lambda.isFinite() || lambda <= 0.0) return 0L

        // Knuth's algorithm: expected O(lambda).
        val l = exp(-lambda)
        var k = 0L
        var p = 1.0
        do {
            k++
            p *= random.nextDouble()
        } while (p > l)
        return k - 1
    }
}
