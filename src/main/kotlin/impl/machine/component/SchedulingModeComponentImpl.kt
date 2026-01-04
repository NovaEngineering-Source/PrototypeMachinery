package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineTypeExecutionModeDefaults
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.SchedulingModeComponent
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode

public class SchedulingModeComponentImpl(
    override val owner: MachineInstance,
    override val type: MachineComponentType<*>
) : SchedulingModeComponent {

    override val provider: Any? = null

    override var executionMode: ExecutionMode =
        (owner.type as? MachineTypeExecutionModeDefaults)?.defaultExecutionMode ?: ExecutionMode.CONCURRENT
}
