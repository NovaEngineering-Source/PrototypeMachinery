package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * Matches if the block state has a TileEntity.
 */
public object HasTileEntityPredicate : BlockPredicate {
    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean {
        val st = context.machine.blockEntity.world.getBlockState(pos)
        return try {
            st.block.hasTileEntity(st)
        } catch (_: Throwable) {
            false
        }
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate = this
}
