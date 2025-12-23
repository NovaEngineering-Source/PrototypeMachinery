package github.kasuminova.prototypemachinery.api.machine.structure.pattern

import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World

/**
 * # StructurePattern - Block predicate map (relative coordinates)
 * # StructurePattern - 方块谓词模式（相对坐标）
 *
 * A structure pattern is a mapping from relative positions (controller origin = (0,0,0)) to [BlockPredicate].
 * It is used by matchers to validate whether a region in the world satisfies the structure.
 *
 * 结构模式是“相对坐标（控制器原点 = (0,0,0)） -> [BlockPredicate]”的映射。
 * 匹配器通过它来验证世界中的一片区域是否满足结构要求。
 *
 * - [isAreaLoaded] provides a fast-fail guard to avoid expensive predicate iteration on unloaded chunks.
 * - [transform] enables orientation/rotation support (e.g. 24-way facing).
 */
public interface StructurePattern {

    public val blocks: Map<BlockPos, BlockPredicate>

    /**
     * Minimum relative position covered by this pattern.
     * 此 pattern 覆盖范围的最小相对坐标。
     */
    public val minPos: BlockPos
        get() = computeBounds(blocks).min

    /**
     * Maximum relative position covered by this pattern.
     * 此 pattern 覆盖范围的最大相对坐标。
     */
    public val maxPos: BlockPos
        get() = computeBounds(blocks).max

    /**
     * Fast check: are all chunks/blocks in the pattern's bounding box loaded?
     * 快速检查：pattern 覆盖的包围盒区块/方块是否全部已加载。
     *
     * This is intended to be called BEFORE iterating predicates.
     * 该方法应在遍历 predicate 之前调用。
     */
    public fun isAreaLoaded(world: World, origin: BlockPos): Boolean {
        val from = origin.add(minPos)
        val to = origin.add(maxPos)
        // allowEmpty = false: do NOT force-load chunks.
        return world.isAreaLoaded(from, to, false)
    }

    public fun transform(rotation: (EnumFacing) -> EnumFacing): StructurePattern

    private data class Bounds(val min: BlockPos, val max: BlockPos)

    private fun computeBounds(blocks: Map<BlockPos, BlockPredicate>): Bounds {
        if (blocks.isEmpty()) {
            val zero = BlockPos(0, 0, 0)
            return Bounds(zero, zero)
        }

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

        return Bounds(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ))
    }

}
