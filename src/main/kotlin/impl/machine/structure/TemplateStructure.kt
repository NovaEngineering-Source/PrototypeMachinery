package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public class TemplateStructure(
    override val id: String,
    override val orientation: StructureOrientation,
    override val offset: BlockPos,
    public val pattern: StructurePattern,
    override val validators: List<StructureValidator> = emptyList(),
    override val children: List<MachineStructure> = emptyList()
) : MachineStructure {
    override fun createData(): StructureInstanceData {
        return object : StructureInstanceData {
            override val orientation: StructureOrientation = this@TemplateStructure.orientation
        }
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure {
        return TemplateStructure(
            id,
            orientation.transform(rotation),
            StructureUtils.rotatePos(offset, rotation),
            pattern.transform(rotation),
            validators,
            children.map { it.transform(rotation) }
        )
    }

}