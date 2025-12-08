package github.kasuminova.prototypemachinery.impl.machine.structure

import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public object StructureUtils {

    public fun rotatePos(pos: BlockPos, rotation: (EnumFacing) -> EnumFacing): BlockPos {
        val xVec = getDirectionVector(rotation(EnumFacing.EAST))
        val yVec = getDirectionVector(rotation(EnumFacing.UP))
        val zVec = getDirectionVector(rotation(EnumFacing.SOUTH))

        return BlockPos(
            pos.x * xVec.x + pos.y * yVec.x + pos.z * zVec.x,
            pos.x * xVec.y + pos.y * yVec.y + pos.z * zVec.y,
            pos.x * xVec.z + pos.y * yVec.z + pos.z * zVec.z
        )
    }

    private fun getDirectionVector(facing: EnumFacing): BlockPos {
        val vec = facing.directionVec
        return BlockPos(vec.x, vec.y, vec.z)
    }

}
