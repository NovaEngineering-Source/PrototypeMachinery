package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.PreviewableBlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateWithNbtRequirement
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * Block-state predicate with additional TileEntity NBT constraints.
 *
 * - First matches the exact block state (same semantics as [StatedBlockPredicate]).
 * - Then (if [nbtConstraints] is not empty) requires a TileEntity at [pos], and checks that
 *   each key exists and matches the expected string value.
 *
 * Notes / 限制：
 * - The JSON schema currently stores NBT as a simplified `Map<String, String>`.
 * - Matching is shallow (no nested paths). Keys are looked up on the TE root tag.
 * - Value comparison is best-effort:
 *   - If the tag is a string, compares [NBTTagCompound.getString].
 *   - Otherwise compares [NBTBase.toString] (MC's SNBT-like debug form).
 */
public class StatedBlockNbtPredicate(
	public val state: IBlockState,
	public val nbtConstraints: Map<String, String>
) : PreviewableBlockPredicate {

	override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean {
		val world = context.machine.blockEntity.world
		if (world.getBlockState(pos) !== state) return false

		if (nbtConstraints.isEmpty()) return true

		val te: TileEntity = world.getTileEntity(pos) ?: return false
		val tag = NBTTagCompound()
		te.writeToNBT(tag)

		for ((k, expected) in nbtConstraints) {
			if (!tag.hasKey(k)) return false

			val base: NBTBase = tag.getTag(k) ?: return false
			val actual = when (base) {
				is NBTTagString -> tag.getString(k)
				else -> base.toString()
			}

			if (actual != expected) return false
		}

		return true
	}

	override fun toRequirement(): BlockRequirement {
		val block = state.block
		val id = requireNotNull(block.registryName) { "Unregistered block in StatedBlockNbtPredicate: $block" }
		@Suppress("DEPRECATION")
		val meta = block.getMetaFromState(state)
		val props = state.propertyKeys.associate { prop: IProperty<*> ->
			val v = state.getValue(prop)
			prop.name to v.toString()
		}
		return ExactBlockStateWithNbtRequirement(
			blockId = id,
			meta = meta,
			properties = props,
			nbtConstraints = nbtConstraints
		)
	}

	override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate {
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
		return StatedBlockNbtPredicate(newState, nbtConstraints)
	}
}