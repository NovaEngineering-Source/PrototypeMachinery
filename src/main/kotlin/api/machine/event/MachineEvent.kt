package github.kasuminova.prototypemachinery.api.machine.event

import github.kasuminova.prototypemachinery.api.machine.MachineInstance

/**
 * Represents an event that occurs within the context of a machine.
 */
public interface MachineEvent {

    public val machine: MachineInstance

}