package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.PreviewableBlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.preview.AnyOfRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * A predicate that matches if the world block state equals any of the provided states.
 *
 * 用于表达“多种方块任选其一”的结构需求。
 */
public class AnyOfBlockPredicate(public val states: List<IBlockState>) : PreviewableBlockPredicate {

    init {
        require(states.isNotEmpty()) { "AnyOfBlockPredicate.states must not be empty" }
    }

    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean {
        val st = context.machine.blockEntity.world.getBlockState(pos)
        return states.any { it === st }
    }

    override fun toRequirement(): BlockRequirement {
        val opts = states.map { state ->
            val block = state.block
            val id = requireNotNull(block.registryName) { "Unregistered block in AnyOfBlockPredicate: $block" }
            @Suppress("DEPRECATION")
            val meta = block.getMetaFromState(state)
            val props = state.propertyKeys.associate { prop ->
                val v = state.getValue(prop)
                prop.name to v.toString()
            }
            ExactBlockStateRequirement(id, meta, props)
        }
        return AnyOfRequirement(opts)
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate {
        fun rotateState(state: IBlockState): IBlockState {
            var newState = state
            for (prop in state.propertyKeys) {
                if (prop.valueClass === EnumFacing::class.java) {
                    @Suppress("UNCHECKED_CAST")
                    val directionProp = prop as IProperty<EnumFacing>
                    val current = state.getValue(directionProp)
                    val newFacing = rotation(current)
                    if (directionProp.allowedValues.contains(newFacing)) {
                        newState = newState.withProperty(directionProp, newFacing)
                    }
                }
            }
            return newState
        }

        return AnyOfBlockPredicate(states.map { rotateState(it) })
    }
}
