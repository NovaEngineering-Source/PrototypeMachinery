package github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * Checks shallow TileEntity NBT constraints at the given position.
 *
 * This predicate does NOT check block id/state by itself; combine with other predicates via [CompositeBlockPredicate].
 */
public class TileEntityNbtPredicate(
    public val nbtConstraints: Map<String, String>
) : BlockPredicate {

    override fun matches(context: StructureMatchContext, pos: BlockPos): Boolean {
        if (nbtConstraints.isEmpty()) return true

        val world = context.machine.blockEntity.world
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

    override fun transform(rotation: (EnumFacing) -> EnumFacing): BlockPredicate = this
}
