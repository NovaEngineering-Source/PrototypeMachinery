package github.kasuminova.prototypemachinery.integration.crafttweaker.wrapper

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.integration.crafttweaker.ICraftTweakerMachineType
import net.minecraft.util.ResourceLocation

/**
 * Wrapper adapting ICraftTweakerMachineType to MachineType (bridge shim).
 * 将 ICraftTweakerMachineType 适配为 MachineType 的桥接薄层。
 */
internal class MachineTypeWrapper(
    private val ctMachineType: ICraftTweakerMachineType
) : MachineType {

    override val id: ResourceLocation
        get() = ctMachineType.id

    override val name: String
        get() = ctMachineType.name

    override val structure: MachineStructure
        get() = ctMachineType.structure

    override val componentTypes: Set<MachineComponentType<*>>
        get() = ctMachineType.componentTypes

}
