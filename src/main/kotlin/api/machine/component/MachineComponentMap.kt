package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem

public interface MachineComponentMap {

    public val components: Map<MachineComponentType<*>, MachineComponent>

    public val systems: Map<MachineSystem<*>, MutableSet<MachineComponent>>

    public fun add(component: MachineComponent)

    public fun remove(component: MachineComponent)

    public fun <C : MachineComponent> get(type: MachineComponentType<C>): C?

}