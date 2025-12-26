package github.kasuminova.prototypemachinery.common.util

import net.minecraft.util.EnumFacing

/**
 * Small math helpers for the 24 cube orientations represented by (front, top).
 *
 * Coordinate system (Minecraft):
 * - +X = EAST
 * - +Y = UP
 * - +Z = SOUTH
 *
 * Our rotation steps are defined to match the MatrixStack calls used on client:
 * - Y_POS: rotateY(+90°)
 * - Y_NEG: rotateY(-90°)
 * - X_POS: rotateX(+90°)
 * - X_NEG: rotateX(-90°)
 */
internal object OrientationMath {

    internal enum class RotationStep {
        X_POS,
        X_NEG,
        Y_POS,
        Y_NEG,
    }

    internal data class OrientationKey(
        val front: EnumFacing,
        val top: EnumFacing,
    ) {
        init {
            require(isValid(front, top)) { "Invalid orientation (front=$front, top=$top)" }
        }
    }

    internal fun isValid(front: EnumFacing, top: EnumFacing): Boolean {
        return top != front && top != front.opposite
    }

    /**
     * Normalizes [top] so it is valid for [front].
     *
     * - If already valid: returns as-is
     * - Else: tries UP, then NORTH, then EAST as fallbacks
     */
    internal fun normalizeTop(front: EnumFacing, top: EnumFacing): EnumFacing {
        if (isValid(front, top)) return top

        // Prefer UP when possible (keeps old behavior for horizontal-front machines).
        if (isValid(front, EnumFacing.UP)) return EnumFacing.UP
        if (isValid(front, EnumFacing.NORTH)) return EnumFacing.NORTH
        if (isValid(front, EnumFacing.EAST)) return EnumFacing.EAST

        // Should never happen for 6-direction facings.
        return EnumFacing.UP
    }

    private fun rotateVec(step: RotationStep, x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
        return when (step) {
            RotationStep.X_POS -> Triple(x, -z, y)
            RotationStep.X_NEG -> Triple(x, z, -y)
            RotationStep.Y_POS -> Triple(z, y, -x)
            RotationStep.Y_NEG -> Triple(-z, y, x)
        }
    }

    private fun facingFromVec(x: Int, y: Int, z: Int): EnumFacing {
        return when {
            x == 1 && y == 0 && z == 0 -> EnumFacing.EAST
            x == -1 && y == 0 && z == 0 -> EnumFacing.WEST
            x == 0 && y == 1 && z == 0 -> EnumFacing.UP
            x == 0 && y == -1 && z == 0 -> EnumFacing.DOWN
            x == 0 && y == 0 && z == 1 -> EnumFacing.SOUTH
            x == 0 && y == 0 && z == -1 -> EnumFacing.NORTH
            else -> throw IllegalArgumentException("Invalid facing vector: ($x,$y,$z)")
        }
    }

    private fun rotateFacing(step: RotationStep, facing: EnumFacing): EnumFacing {
        val v = facing.directionVec
        val (nx, ny, nz) = rotateVec(step, v.x, v.y, v.z)
        return facingFromVec(nx, ny, nz)
    }

    internal fun applyStep(front: EnumFacing, top: EnumFacing, step: RotationStep): OrientationKey {
        val newFront = rotateFacing(step, front)
        val newTop = rotateFacing(step, top)
        return OrientationKey(newFront, newTop)
    }

    // Precomputed shortest paths from base (NORTH, UP) to any valid orientation.
    private val pathsFromBase: Map<OrientationKey, List<RotationStep>> = run {
        val base = OrientationKey(EnumFacing.NORTH, EnumFacing.UP)

        val steps = listOf(
            RotationStep.Y_POS,
            RotationStep.Y_NEG,
            RotationStep.X_POS,
            RotationStep.X_NEG,
        )

        val queue = ArrayDeque<OrientationKey>()
        val paths = LinkedHashMap<OrientationKey, List<RotationStep>>()

        queue.add(base)
        paths[base] = emptyList()

        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            val curPath = paths[cur] ?: emptyList()

            for (s in steps) {
                val next = applyStep(cur.front, cur.top, s)
                if (next !in paths) {
                    paths[next] = curPath + s
                    queue.add(next)
                }
            }
        }

        // Sanity: cube orientation group should have exactly 24 unique orientations.
        // If this changes, something is wrong with our step definitions.
        require(paths.size == 24) { "Expected 24 orientations, got ${paths.size}" }

        paths
    }

    internal fun stepsFromBase(front: EnumFacing, top: EnumFacing): List<RotationStep> {
        val normalizedTop = normalizeTop(front, top)
        val key = OrientationKey(front, normalizedTop)
        return pathsFromBase[key]
            ?: throw IllegalStateException("No rotation path for orientation: $key")
    }

    internal fun allOrientations(): Set<OrientationKey> = pathsFromBase.keys
}
