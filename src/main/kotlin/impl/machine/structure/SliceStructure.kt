package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

public class SliceStructure(
    override val id: String,
    override val orientation: StructureOrientation,
    override val offset: BlockPos,
    public val pattern: StructurePattern,
    public val minCount: Int,
    public val maxCount: Int,
    override val validators: List<StructureValidator> = emptyList(),
    override val children: List<MachineStructure> = emptyList()
) : MachineStructure {

    override fun createData(): StructureInstanceData {
        return object : StructureInstanceData {
            override val orientation: StructureOrientation = this@SliceStructure.orientation
        }
    }

    override fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure {
        return SliceStructure(
            id,
            orientation.transform(rotation),
            StructureUtils.rotatePos(offset, rotation),
            pattern.transform(rotation),
            minCount,
            maxCount,
            validators,
            children.map { it.transform(rotation) }
        )
    }

}
