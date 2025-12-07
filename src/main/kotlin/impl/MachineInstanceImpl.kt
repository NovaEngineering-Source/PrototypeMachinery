package github.kasuminova.prototypemachinery.impl

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.impl.machine.component.MachineComponentMapImpl
import net.minecraft.tileentity.TileEntity

public class MachineInstanceImpl(
    override val tile: TileEntity
) : MachineInstance {

    override val type: MachineType
        get() = TODO("Not yet implemented")

    override val componentMap: MachineComponentMapImpl = MachineComponentMapImpl()

    override val attributeMap: MachineAttributeMap
        get() = TODO("Not yet implemented")

    override val activeProcesses: Collection<RecipeProcess> = ArrayList()

}