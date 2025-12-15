package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.PreviewableBlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public class StatedBlockPredicate(public val state: IBlockState) : PreviewableBlockPredicate {

    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean = context.machine.blockEntity.world.getBlockState(pos) === state

    override fun toRequirement(): BlockRequirement {
        val block = state.block
        val id = requireNotNull(block.registryName) { "Unregistered block in StatedBlockPredicate: $block" }
        @Suppress("DEPRECATION")
        val meta = block.getMetaFromState(state)
        val props = state.propertyKeys.associate { prop ->
            // We only need a stable string form for preview/BOM grouping.
            val v = state.getValue(prop)
            prop.name to v.toString()
        }
        return ExactBlockStateRequirement(id, meta, props)
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate {
        var newState = state
        for (prop in state.propertyKeys) {
            // Handle EnumFacing properties (Direction)
            if (prop.valueClass === EnumFacing::class.java) {
                @Suppress("UNCHECKED_CAST")
                val directionProp = prop as IProperty<EnumFacing>
                val current = state.getValue(directionProp)
                val newFacing = rotation(current)
                if (directionProp.allowedValues.contains(newFacing)) {
                    newState = newState.withProperty(directionProp, newFacing)
                }
            }
            // TODO: Handle EnumAxis properties if needed
        }
        return StatedBlockPredicate(newState)
    }

}