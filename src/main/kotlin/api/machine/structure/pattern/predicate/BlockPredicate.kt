package github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public interface BlockPredicate {

    public fun matches(context: StructureMatchContext, pos: BlockPos): Boolean

    /**
     * Transforms this predicate based on the given rotation function.
     * The rotation function maps an original EnumFacing to a rotated EnumFacing.
     * This allows for 24-way rotation support.
     */
    public fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate

}