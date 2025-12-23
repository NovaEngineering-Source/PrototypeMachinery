package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode

public interface EnergyContainerComponent : MachineComponent {

    public val capacity: Long

    public val stored: Long

    public fun isAllowedPortMode(mode: PortMode): Boolean

    public fun insertEnergy(amount: Long, mode: TransactionMode): Long

    public fun extractEnergy(amount: Long, mode: TransactionMode): Long

}
