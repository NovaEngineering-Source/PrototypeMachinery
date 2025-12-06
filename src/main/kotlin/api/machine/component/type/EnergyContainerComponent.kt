package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent

public interface EnergyContainerComponent : MachineComponent {

    public val capacity: Long

    public val stored: Long

    public fun insertEnergy(amount: Long, simulate: Boolean): Long

    public fun extractEnergy(amount: Long, simulate: Boolean): Long

}
