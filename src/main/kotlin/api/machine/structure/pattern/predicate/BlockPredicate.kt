package github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
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

    public class Stated(public val state: IBlockState) : BlockPredicate {

        override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean = context.machine.tile.world.getBlockState(pos) === state

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
            return Stated(newState)
        }

    }

}