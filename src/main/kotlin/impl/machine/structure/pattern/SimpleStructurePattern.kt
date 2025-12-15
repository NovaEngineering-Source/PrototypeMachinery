package github.kasuminova.prototypemachinery.impl.machine.structure.pattern

import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public class SimpleStructurePattern(
    override val blocks: Map<BlockPos, BlockPredicate>
) : StructurePattern {

    private val boundsMinMax: Pair<BlockPos, BlockPos> = run {
        if (blocks.isEmpty()) {
            val zero = BlockPos(0, 0, 0)
            zero to zero
        } else {
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var maxZ = Int.MIN_VALUE
            for (pos in blocks.keys) {
                if (pos.x < minX) minX = pos.x
                if (pos.y < minY) minY = pos.y
                if (pos.z < minZ) minZ = pos.z
                if (pos.x > maxX) maxX = pos.x
                if (pos.y > maxY) maxY = pos.y
                if (pos.z > maxZ) maxZ = pos.z
            }
            BlockPos(minX, minY, minZ) to BlockPos(maxX, maxY, maxZ)
        }
    }

    override val minPos: BlockPos
        get() = boundsMinMax.first

    override val maxPos: BlockPos
        get() = boundsMinMax.second

    override fun transform(rotation: (EnumFacing) -> EnumFacing): StructurePattern {
        val newBlocks = mutableMapOf<BlockPos, BlockPredicate>()
        for ((pos, predicate) in blocks) {
            val newPos = rotatePos(pos, rotation)
            val newPredicate = predicate.transform(rotation)
            newBlocks[newPos] = newPredicate
        }
        return SimpleStructurePattern(newBlocks)
    }

    private fun rotatePos(pos: BlockPos, rotation: (EnumFacing) -> EnumFacing): BlockPos {
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
