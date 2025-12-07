package github.kasuminova.prototypemachinery.api.machine.structure.match

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData

public interface StructureMatchContext {

    public val machine: MachineInstance

    public val currentMatching: MachineStructure

    public val currentMatchingData: StructureInstanceData

}