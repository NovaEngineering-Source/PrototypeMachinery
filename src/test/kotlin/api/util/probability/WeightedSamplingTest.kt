package github.kasuminova.prototypemachinery.api.util.probability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class WeightedSamplingTest {

    @Test
    fun `sampleWithoutReplacement returns unique elements`() {
        val r = Random(123)
        val c = listOf(
            WeightedSampling.Weighted("a", 1),
            WeightedSampling.Weighted("b", 1),
            WeightedSampling.Weighted("c", 1),
        )

        val picked = WeightedSampling.sampleWithoutReplacement(r, c, 3)
        assertEquals(3, picked.size)
        assertEquals(3, picked.toSet().size)
    }

    @Test
    fun `sampleWithoutReplacement ignores non-positive weights`() {
        val r = Random(1)
        val c = listOf(
            WeightedSampling.Weighted("a", 0),
            WeightedSampling.Weighted("b", -10),
            WeightedSampling.Weighted("c", 5),
        )

        val picked = WeightedSampling.sampleWithoutReplacement(r, c, 3)
        assertEquals(listOf("c"), picked)
    }

    @Test
    fun `sampleWithoutReplacement respects requested count cap`() {
        val r = Random(1)
        val c = listOf(
            WeightedSampling.Weighted("a", 1),
            WeightedSampling.Weighted("b", 1),
        )

        val picked = WeightedSampling.sampleWithoutReplacement(r, c, 10)
        assertEquals(2, picked.size)
        assertTrue(picked.containsAll(listOf("a", "b")))
    }
}
