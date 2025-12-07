package github.kasuminova.prototypemachinery.api.machine.structure.pattern

import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public interface StructurePattern {

    public val blocks: Map<BlockPos, BlockPredicate>

    public fun transform(rotation: (EnumFacing) -> EnumFacing): StructurePattern

}
