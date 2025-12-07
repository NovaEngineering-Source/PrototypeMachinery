package github.kasuminova.prototypemachinery.api.machine

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity

public interface MachineInstance {

    public val type: MachineType

    public val blockEntity: BlockEntity

    public val componentMap: MachineComponentMap

    public val attributeMap: MachineAttributeMap

    public val activeProcesses: Collection<RecipeProcess>

}