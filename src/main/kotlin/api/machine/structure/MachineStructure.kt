package github.kasuminova.prototypemachinery.api.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import net.minecraft.util.math.BlockPos

public interface MachineStructure {

    public val id: String

    public val offset: BlockPos

    public val pattern: StructurePattern?

    public val validators: List<StructureValidator>

    public val children: List<MachineStructure>

    public val minCount: Int

    public val maxCount: Int

}