package github.kasuminova.prototypemachinery.api.machine.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.event.MachineEvent
import github.kasuminova.prototypemachinery.api.machine.event.MachineTickEvent
import github.kasuminova.prototypemachinery.api.system.ComponentSystem

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
public interface MachineSystem<C : MachineComponent> : ComponentSystem<MachineInstance, C> {

    override fun onPreTick(machine: MachineInstance, component: C) {
        onEvent(machine, component, MachineTickEvent.Pre(machine))
    }

    override fun onTick(machine: MachineInstance, component: C) {
        onEvent(machine, component, MachineTickEvent.Normal(machine))
    }

    override fun onPostTick(machine: MachineInstance, component: C) {
        onEvent(machine, component, MachineTickEvent.Post(machine))
    }

    /**
     * Called when a machine event is pushed to the system.
     */
    public fun onEvent(machine: MachineInstance, component: C, event: MachineEvent) {}

}
