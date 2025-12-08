package github.kasuminova.prototypemachinery.api.machine.structure

import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumFacing.DOWN
import net.minecraft.util.EnumFacing.EAST
import net.minecraft.util.EnumFacing.NORTH
import net.minecraft.util.EnumFacing.SOUTH
import net.minecraft.util.EnumFacing.UP
import net.minecraft.util.EnumFacing.WEST

/**
 * Represents the orientation of a structure in 3D space.
 * Defined by the direction the structure is facing (Front) and the direction of its top (Top).
 *
 * Graphical Representation:
 *
 *       T (Top)
 *       |
 *       |
 * L ----+---- R (Right)
 *      /
 *     /
 *    F (Front)
 *
 * Transformation Order Example:
 * - Base: Front=NORTH, Top=UP
 * - rotate(UP) [Yaw]: Front=EAST, Top=UP
 * - rotate(EAST) [Pitch]: Front=DOWN, Top=UP -> Wait, if Front=EAST, Top=UP, Right=SOUTH.
 *   Rotate around EAST (Front): Front=EAST, Top=SOUTH. (Roll)
 */
public data class StructureOrientation(
    public val front: EnumFacing = NORTH,
    public val top: EnumFacing = UP
) {

    init {
        require(front != top && front != top.opposite) { "Front and Top cannot be the same or opposite axis." }
    }

    /**
     * Gets the direction to the right of the structure.
     * Calculated as Front x Top.
     */
    public val right: EnumFacing get() = crossProduct(front, top)

    /**
     * Transforms this orientation using the given rotation function.
     */
    public fun transform(rotation: (EnumFacing) -> EnumFacing): StructureOrientation {
        return StructureOrientation(rotation(front), rotation(top))
    }

    /**
     * Rotates the orientation 90 degrees clockwise around the given global axis.
     *
     * @param axis The axis to rotate around.
     * @return A new StructureOrientation representing the rotated state.
     */
    public fun rotate(axis: EnumFacing): StructureOrientation {
        return StructureOrientation(rotate90(front, axis), rotate90(top, axis))
    }

    private fun rotate90(target: EnumFacing, axis: EnumFacing): EnumFacing {
        // If the target is on the rotation axis, it doesn't change.
        if (target.axis == axis.axis) return target

        // For 90 degree rotation around an axis: Result = Target x Axis
        // Example: Rotate NORTH around UP. NORTH x UP = EAST.
        // Example: Rotate EAST around UP. EAST x UP = SOUTH.
        return crossProduct(target, axis)
    }

    private fun crossProduct(a: EnumFacing, b: EnumFacing): EnumFacing = when (a) {
        DOWN -> when (b) {
            NORTH -> EAST
            SOUTH -> WEST
            WEST -> NORTH
            EAST -> SOUTH
            else -> throw IllegalArgumentException("Invalid cross product: $a x $b")
        }

        UP -> when (b) {
            NORTH -> WEST
            SOUTH -> EAST
            WEST -> SOUTH
            EAST -> NORTH
            else -> throw IllegalArgumentException("Invalid cross product: $a x $b")
        }

        NORTH -> when (b) {
            UP -> EAST
            DOWN -> WEST
            WEST -> UP
            EAST -> DOWN
            else -> throw IllegalArgumentException("Invalid cross product: $a x $b")
        }

        SOUTH -> when (b) {
            UP -> WEST
            DOWN -> EAST
            WEST -> DOWN
            EAST -> UP
            else -> throw IllegalArgumentException("Invalid cross product: $a x $b")
        }

        WEST -> when (b) {
            UP -> NORTH
            DOWN -> SOUTH
            NORTH -> DOWN
            SOUTH -> UP
            else -> throw IllegalArgumentException("Invalid cross product: $a x $b")
        }

        EAST -> when (b) {
            UP -> SOUTH
            DOWN -> NORTH
            NORTH -> UP
            SOUTH -> DOWN
            else -> throw IllegalArgumentException("Invalid cross product: $a x $b")
        }
    }

}