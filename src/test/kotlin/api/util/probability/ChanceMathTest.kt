package github.kasuminova.prototypemachinery.api.util.probability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Random

class ChanceMathTest {

    @Test
    fun `maxTimes is conservative upper bound`() {
        val k = 7
        val chance = 250.0 // c=2.5 => ceil(c)=3 => 21
        assertEquals(21L, ChanceMath.maxTimes(k, chance))

        val r = Random(123)
        repeat(100) {
            val times = ChanceMath.sampleTimes(r, k, chance)
            assertTrue(times in 0..ChanceMath.maxTimes(k, chance))
        }
    }

    @Test
    fun `chance 100 percent equals parallels`() {
        val k = 5
        val r = Random(1)
        repeat(10) {
            assertEquals(k.toLong(), ChanceMath.sampleTimes(r, k, 100.0))
        }
    }

    @Test
    fun `chance below 100 is within 0 to parallels`() {
        val k = 10
        val r = Random(42)
        repeat(50) {
            val times = ChanceMath.sampleTimes(r, k, 30.0)
            assertTrue(times in 0..k.toLong())
        }
    }
}
