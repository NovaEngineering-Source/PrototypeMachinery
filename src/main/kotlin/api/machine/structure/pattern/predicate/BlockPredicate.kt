package github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * # BlockPredicate - Single block requirement predicate
 * # BlockPredicate - 单格方块需求谓词
 *
 * Determines whether a specific world position satisfies a structure requirement.
 *
 * 用于判断某个世界坐标是否满足结构的单格需求。
 */
public interface BlockPredicate {

    public fun matches(context: StructureMatchContext, pos: BlockPos): Boolean

    /**
     * Transforms this predicate based on the given rotation function.
     * The rotation function maps an original EnumFacing to a rotated EnumFacing.
     * This allows for 24-way rotation support.
     *
     * 基于给定的“朝向旋转映射”对 predicate 做变换。
     * rotation 会把原始 EnumFacing 映射为旋转后的 EnumFacing，从而支持 24 向（front+top）旋转。
     */
    public fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate

}