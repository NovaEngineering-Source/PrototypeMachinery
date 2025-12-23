package github.kasuminova.prototypemachinery.api.util.probability

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.Random
import java.util.concurrent.TimeUnit
import kotlin.math.floor

/**
 * Microbenchmarks for chance -> times sampling.
 *
 * We benchmark:
 * - current implementation: [ChanceMath.sampleTimes]
 * - exact Bernoulli baseline for small parallels (old behavior for the fractional part)
 * - [BinomialSampler] alone across common parameter regimes
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
// Keep this suite relatively short; we mainly want relative ordering across algorithms.
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
open class ChanceMathJmhBenchmark {

    @State(Scope.Thread)
    open class SampleTimesState {
        @Param("false", "true")
        var enableExactSmallBinomial: Boolean = false

        @Param("16", "256", "4096", "65536")
        var parallels: Int = 1

        @Param("30.0", "250.0", "1000.0")
        var chancePercent: Double = 30.0

        // Fixed seed for stable results across runs.
        private val seed: Long = 12345L

        lateinit var random: Random

        @Setup(Level.Iteration)
        fun setupIteration() {
            ProbabilityTuning.enableExactSmallBinomial = enableExactSmallBinomial
            random = Random(seed)
        }
    }

    /**
     * Current gameplay sampler (fast for huge parallels).
     */
    @Benchmark
    fun chanceMath_sampleTimes_current(state: SampleTimesState, bh: Blackhole) {
        bh.consume(ChanceMath.sampleTimes(state.random, state.parallels, state.chancePercent))
    }

    @State(Scope.Thread)
    open class SampleTimesExactBernoulliSmallState {
        @Param("16", "256", "1024")
        var parallels: Int = 1

        @Param("30.0", "250.0")
        var chancePercent: Double = 30.0

        private val seed: Long = 12345L

        lateinit var random: Random

        @Setup(Level.Iteration)
        fun setupIteration() {
            random = Random(seed)
        }
    }

    /**
     * Exact baseline for the fractional part (O(parallels)).
     *
     * Kept for small parallels only, to avoid turning the benchmark suite into a stress test.
     */
    @Benchmark
    fun sampleTimes_exactBernoulli_baseline_small(state: SampleTimesExactBernoulliSmallState, bh: Blackhole) {
        val times = sampleTimesExactBernoulli(state.random, state.parallels, state.chancePercent)
        bh.consume(times)
    }

    @State(Scope.Thread)
    open class BinomialState {
        @Param("false", "true")
        var enableExactSmallBinomial: Boolean = false

        @Param("4096", "1000000")
        var n: Int = 1024

        @Param("0.01", "0.3", "0.8")
        var p: Double = 0.3

        private val seed: Long = 12345L
        lateinit var random: Random

        @Setup(Level.Iteration)
        fun setupIteration() {
            ProbabilityTuning.enableExactSmallBinomial = enableExactSmallBinomial
            random = Random(seed)
        }
    }

    @Benchmark
    fun binomialSampler_sample(state: BinomialState, bh: Blackhole) {
        bh.consume(BinomialSampler.sample(state.random, state.n, state.p))
    }

    @State(Scope.Thread)
    open class RandomCostState {
        private val seed: Long = 12345L
        lateinit var random: Random

        @Setup(Level.Iteration)
        fun setupIteration() {
            random = Random(seed)
        }
    }

    /**
     * Roughly measures the cost of nextDouble() itself.
     */
    @Benchmark
    fun random_nextDouble_cost(state: RandomCostState, bh: Blackhole) {
        bh.consume(state.random.nextDouble())
    }

    /**
     * Roughly measures the cost of nextGaussian() itself.
     *
     * Note: java.util.Random caches one gaussian, so steady-state cost is amortized.
     */
    @Benchmark
    fun random_nextGaussian_cost(state: RandomCostState, bh: Blackhole) {
        bh.consume(state.random.nextGaussian())
    }

    private fun sampleTimesExactBernoulli(random: Random, parallels: Int, chancePercent: Double): Long {
        if (parallels <= 0) return 0L
        if (!chancePercent.isFinite()) return 0L
        if (chancePercent <= 0.0) return 0L

        val c = chancePercent / 100.0
        val g = floor(c).toLong().coerceAtLeast(0L)
        val p = (c - g.toDouble()).coerceIn(0.0, 1.0)

        var extra = 0L
        if (p > 0.0) {
            for (i in 0 until parallels) {
                if (random.nextDouble() < p) extra++
            }
        }
        return g * parallels.toLong() + extra
    }

}
