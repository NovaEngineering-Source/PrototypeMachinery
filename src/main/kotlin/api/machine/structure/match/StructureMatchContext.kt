package github.kasuminova.prototypemachinery.api.machine.structure.match

import github.kasuminova.prototypemachinery.api.machine.MachineInstance

public interface StructureMatchContext {

    public val machine: MachineInstance

    public val customData: Map<String, Any>

}