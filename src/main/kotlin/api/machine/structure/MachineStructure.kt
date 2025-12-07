package github.kasuminova.prototypemachinery.api.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public interface MachineStructure {

    public val id: String

    public val orientation: StructureOrientation

    public val offset: BlockPos

    public val validators: List<StructureValidator>

    public val children: List<MachineStructure>

    public fun createData(): StructureInstanceData

    public fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure

}