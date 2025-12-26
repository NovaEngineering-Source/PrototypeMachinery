package github.kasuminova.prototypemachinery.common.util

import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import net.minecraft.util.EnumFacing

/**
 * Utility for the new twist-based orientation system.
 *
 * ## Orientation Model
 * - **FACING**: The direction the controller's front face points (6 values)
 * - **TWIST**: Clockwise rotation steps around the FACING axis (0-3 → 0°/90°/180°/270°)
 *
 * Together, (FACING, TWIST) uniquely identifies one of 24 cube orientations,
 * equivalent to (front, top) representation used elsewhere.
 *
 * ## Coordinate Conventions
 * - "Clockwise" is defined as viewed from OUTSIDE the block looking at its front face
 * - Twist=0 means the canonical "up" direction for that facing
 */
internal object TwistMath {

    /**
     * Computes the "top" direction for a given (facing, twist) pair.
     *
     * The canonical top (twist=0) for each facing:
     * - NORTH/SOUTH/EAST/WEST → UP
     * - UP → NORTH
     * - DOWN → SOUTH
     */
    internal fun getTopFromTwist(facing: EnumFacing, twist: Int): EnumFacing {
        val canonical = canonicalTopFor(facing)
        return rotateAroundAxis(canonical, facing, twist)
    }

    /**
     * Computes the twist value (0-3) for a given (facing, top) pair.
     *
     * Returns 0 if top is the canonical top for this facing,
     * 1/2/3 for subsequent clockwise rotations.
     */
    internal fun getTwistFromTop(facing: EnumFacing, top: EnumFacing): Int {
        require(OrientationMath.isValid(facing, top)) { "Invalid orientation (facing=$facing, top=$top)" }

        val canonical = canonicalTopFor(facing)

        // Find how many 90° clockwise rotations are needed to go from canonical to top
        var current = canonical
        for (i in 0..3) {
            if (current == top) return i
            current = rotateAroundAxis(current, facing, 1)
        }

        // Should never happen for valid orientations
        return 0
    }

    /**
     * Converts (facing, twist) to a StructureOrientation for structure matching etc.
     */
    internal fun toStructureOrientation(facing: EnumFacing, twist: Int): StructureOrientation {
        val top = getTopFromTwist(facing, twist)
        return StructureOrientation(front = facing, top = top)
    }

    /**
     * Returns the next twist value (cycling 0→1→2→3→0).
     */
    internal fun nextTwist(twist: Int): Int = (twist + 1) and 3

    /**
     * Returns the previous twist value (cycling 0→3→2→1→0).
     */
    internal fun prevTwist(twist: Int): Int = (twist + 3) and 3

    /**
     * The canonical "top" for a given facing when twist=0.
     */
    private fun canonicalTopFor(facing: EnumFacing): EnumFacing = when (facing) {
        EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST -> EnumFacing.UP
        EnumFacing.UP -> EnumFacing.NORTH
        EnumFacing.DOWN -> EnumFacing.SOUTH
    }

    /**
     * Rotates a direction [dir] around [axis] by [steps] × 90° clockwise
     * (as viewed from outside, looking at the face pointed by [axis]).
     */
    private fun rotateAroundAxis(dir: EnumFacing, axis: EnumFacing, steps: Int): EnumFacing {
        if (dir.axis == axis.axis) return dir // On the axis, rotation doesn't change it

        var result = dir
        val normalizedSteps = steps and 3
        repeat(normalizedSteps) {
            result = rotateCW90(result, axis)
        }
        return result
    }

    /**
     * Single 90° clockwise rotation around [axis].
     *
     * "Clockwise" is defined by looking in the direction [axis] points.
     * E.g., for axis=NORTH (looking towards -Z), UP rotates to EAST.
     */
    private fun rotateCW90(dir: EnumFacing, axis: EnumFacing): EnumFacing {
        // Cross product: result = dir × axis gives the perpendicular,
        // but the direction depends on handedness.
        // We use the same convention as StructureOrientation.
        return StructureOrientation(front = axis, top = dir).right
    }
}
