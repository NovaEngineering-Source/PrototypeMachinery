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

            // Handle EnumFacing.Axis properties (Axis)
            // Some blocks store orientation as an axis instead of a full facing.
            if (prop.valueClass === EnumFacing.Axis::class.java) {
                @Suppress("UNCHECKED_CAST")
                val axisProp = prop as IProperty<EnumFacing.Axis>
                val currentAxis = state.getValue(axisProp)

                // Pick a representative facing for the axis, rotate it, then project back to axis.
                val sample = when (currentAxis) {
                    EnumFacing.Axis.X -> EnumFacing.EAST
                    EnumFacing.Axis.Y -> EnumFacing.UP
                    EnumFacing.Axis.Z -> EnumFacing.NORTH
                }
                val newAxis = rotation(sample).axis
                if (axisProp.allowedValues.contains(newAxis)) {
                    newState = newState.withProperty(axisProp, newAxis)
                }
            }
        }
        return StatedBlockPredicate(newState)
    }

}