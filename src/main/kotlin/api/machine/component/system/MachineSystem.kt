package github.kasuminova.prototypemachinery.api.machine.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.system.ComponentSystem

@Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
public interface MachineSystem<C : MachineComponent> : ComponentSystem<MachineInstance, C> {

    override fun onPreTick(machine: MachineInstance, component: C)

    override fun onTick(machine: MachineInstance, component: C)

    override fun onPostTick(machine: MachineInstance, component: C)

}
