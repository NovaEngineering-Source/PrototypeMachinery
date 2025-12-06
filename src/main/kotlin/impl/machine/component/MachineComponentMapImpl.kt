package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem

public class MachineComponentMapImpl : MachineComponentMap {

    override val components: MutableMap<MachineComponentType<*>, MachineComponent> = mutableMapOf()

    override val systems: MutableMap<MachineSystem<*>, MutableSet<MachineComponent>> = mutableMapOf()

    override fun add(component: MachineComponent) {
        components[component.type] = component
        @Suppress("UNCHECKED_CAST")
        val system = component.type.system as MachineSystem<MachineComponent>
        systems.computeIfAbsent(system) { mutableSetOf() }.add(component)
    }

    override fun remove(component: MachineComponent) {
        components.remove(component.type)
        val componentSet = systems[component.type.system]
        if (componentSet != null) {
            componentSet.remove(component)
            if (componentSet.isEmpty()) {
                systems.remove(component.type.system)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <C : MachineComponent> get(type: MachineComponentType<C>): C? {
        return components[type] as? C
    }

}