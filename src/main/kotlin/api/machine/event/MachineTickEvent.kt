package github.kasuminova.prototypemachinery.api.machine.event

import github.kasuminova.prototypemachinery.api.machine.MachineInstance

public sealed interface MachineTickEvent : MachineEvent {

    public class Pre(override val machine: MachineInstance) : MachineTickEvent

    public class Normal(override val machine: MachineInstance) : MachineTickEvent

    public class Post(override val machine: MachineInstance) : MachineTickEvent

}
