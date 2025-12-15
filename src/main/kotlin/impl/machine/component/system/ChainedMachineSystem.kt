package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem

/**
 * Runs multiple [MachineSystem] instances in-order as a single system.
 *
 * This is a pragmatic wiring tool when a component type currently only supports a single [MachineSystem]
 * but we want to stage responsibilities (e.g. scan -> process) without rewriting the ECS dispatcher.
 */
public class ChainedMachineSystem<C : MachineComponent>(
    private val systems: List<MachineSystem<C>>
) : MachineSystem<C> {

    init {
        require(systems.isNotEmpty()) { "ChainedMachineSystem requires at least one system." }
    }

    override fun onPreTick(machine: MachineInstance, component: C) {
        for (sys in systems) {
            sys.onPreTick(machine, component)
        }
    }

    override fun onTick(machine: MachineInstance, component: C) {
        for (sys in systems) {
            sys.onTick(machine, component)
        }
    }

    override fun onPostTick(machine: MachineInstance, component: C) {
        for (sys in systems) {
            sys.onPostTick(machine, component)
        }
    }

    override val runAfter: Set<Class<out MachineSystem<*>>>
        get() = systems.flatMap { it.runAfter }.toSet()

    override val runBefore: Set<Class<out MachineSystem<*>>>
        get() = systems.flatMap { it.runBefore }.toSet()
}
