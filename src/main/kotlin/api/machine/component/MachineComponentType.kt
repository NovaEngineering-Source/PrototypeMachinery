package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import net.minecraft.util.ResourceLocation

public interface MachineComponentType<C : MachineComponent> {

    public val id: ResourceLocation

    public val system: MachineSystem<C>

    public fun createComponent(machine: MachineInstance): C

}