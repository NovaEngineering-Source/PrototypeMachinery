package github.kasuminova.prototypemachinery.api.util.probability

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class BinomialSamplerTest {

    @AfterEach
    fun resetTuning() {
        ProbabilityTuning.enableExactSmallBinomial = false
    }

    @Test
    fun `returns 0 for non-positive n`() {
        val r = Random(1)
        assertEquals(0L, BinomialSampler.sample(r, 0, 0.5))
        assertEquals(0L, BinomialSampler.sample(r, -10, 0.5))
    }

    @Test
    fun `clamps for edge p`() {
        val r = Random(1)
        assertEquals(0L, BinomialSampler.sample(r, 10, 0.0))
        assertEquals(10L, BinomialSampler.sample(r, 10, 1.0))
    }

    @Test
    fun `exact for small n via Bernoulli trials`() {
        val n = 1000
        val p = 0.37
        val seed = 123456789L

        ProbabilityTuning.enableExactSmallBinomial = true

        val r1 = Random(seed)
        val expected = run {
            var c = 0L
            for (i in 0 until n) {
                if (r1.nextDouble() < p) c++
            }
            c
        }

        val r2 = Random(seed)
        val actual = BinomialSampler.sample(r2, n, p)
        assertEquals(expected, actual)
    }

    @Test
    fun `symmetry holds with same seed`() {
        val n = 5000
        val p = 0.73
        val seed = 42L

        val x = BinomialSampler.sample(Random(seed), n, p)
        val y = BinomialSampler.sample(Random(seed), n, 1.0 - p)
        assertEquals(n.toLong(), x + y)
    }

    @Test
    fun `within bounds for large n`() {
        val n = 1_000_000
        val p = 0.3
        val x = BinomialSampler.sample(Random(0), n, p)
        assertTrue(x in 0L..n.toLong())
    }

    @Test
    fun `deterministic sequence for same seed`() {
        val n = 100_000
        val p = 0.4
        val seed = 999L

        val a = Random(seed)
        val b = Random(seed)

        repeat(50) {
            assertEquals(
                BinomialSampler.sample(a, n, p),
                BinomialSampler.sample(b, n, p)
            )
        }
    }
}
