package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * Matches any non-air block.
 */
public object NotAirBlockPredicate : BlockPredicate {
    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean {
        val st = context.machine.blockEntity.world.getBlockState(pos)
        return st.block !== Blocks.AIR && st.material !== net.minecraft.block.material.Material.AIR
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate = this
}
