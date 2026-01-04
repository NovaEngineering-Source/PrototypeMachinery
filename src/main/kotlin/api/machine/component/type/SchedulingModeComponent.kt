package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode
import github.kasuminova.prototypemachinery.impl.machine.component.SchedulingModeComponentImpl
import net.minecraft.util.ResourceLocation

/**
 * Machine scheduling mode component.
 *
 * This component provides a per-machine-instance execution mode used by the task scheduler.
 * By default it is initialized from machine type defaults.
 */
public object SchedulingModeComponentType : MachineComponentType<SchedulingModeComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "scheduling_mode")

    override val system: MachineSystem<SchedulingModeComponent>? = null

    override fun createComponent(machine: MachineInstance): SchedulingModeComponent {
        return SchedulingModeComponentImpl(machine, this)
    }
}

public interface SchedulingModeComponent : MachineComponent {

    /**
     * Current execution mode for this machine instance.
     */
    public var executionMode: ExecutionMode
}
