package github.kasuminova.prototypemachinery.api.machine.structure.logic

import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import net.minecraft.util.math.BlockPos

public fun interface StructureValidator {

    public fun validate(context: StructureMatchContext, offset: BlockPos): Boolean

}
