package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.block.Block
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * Matches if the block id is within a pre-expanded set.
 *
 * Regex expansion is intended to happen during structure loading, not at match time.
 */
public class BlockIdRegexPredicate(
    public val pattern: String,
    public val blocks: Set<Block>
) : BlockPredicate {

    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean {
        val st = context.machine.blockEntity.world.getBlockState(pos)
        return blocks.contains(st.block)
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate = this
}
