package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import net.minecraft.util.math.BlockPos

public class TemplateStructure(
    override val id: String,
    override val offset: BlockPos,
    override val pattern: StructurePattern,
    override val validators: List<StructureValidator> = emptyList(),
    override val children: List<MachineStructure> = emptyList()
) : MachineStructure {

    override val minCount: Int = 1
    override val maxCount: Int = 1

}
