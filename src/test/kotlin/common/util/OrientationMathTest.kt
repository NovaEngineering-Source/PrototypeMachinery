package github.kasuminova.prototypemachinery.common.util

import net.minecraft.util.EnumFacing
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrientationMathTest {

    @Test
    fun `precomputed table has 24 orientations`() {
        assertEquals(24, OrientationMath.allOrientations().size)
    }

    @Test
    fun `stepsFromBase actually reaches target`() {
        val baseFront = EnumFacing.NORTH
        val baseTop = EnumFacing.UP

        for (target in OrientationMath.allOrientations()) {
            val steps = OrientationMath.stepsFromBase(target.front, target.top)

            var curFront = baseFront
            var curTop = baseTop

            for (s in steps) {
                val next = OrientationMath.applyStep(curFront, curTop, s)
                curFront = next.front
                curTop = next.top
            }

            assertEquals(target.front, curFront, "front mismatch for target=$target")
            assertEquals(target.top, curTop, "top mismatch for target=$target")
        }
    }
}
