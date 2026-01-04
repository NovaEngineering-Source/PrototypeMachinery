package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * Matches any block (always true).
 */
public object AnyBlockPredicate : BlockPredicate {
    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean = true
    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate = this
}
