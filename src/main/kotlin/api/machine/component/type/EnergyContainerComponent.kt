package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.util.Action
import github.kasuminova.prototypemachinery.util.IOType

public interface EnergyContainerComponent : MachineComponent {

    public val capacity: Long

    public val stored: Long

    public fun isAllowedIOType(ioType: IOType): Boolean

    public fun insertEnergy(amount: Long, action: Action): Long

    public fun extractEnergy(amount: Long, action: Action): Long

}
