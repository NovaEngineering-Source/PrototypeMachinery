package github.kasuminova.prototypemachinery.api.machine.structure.match

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData

public interface MachineStructureMatcher<S : MachineStructure> {

    public fun match(structure: S, context: StructureMatchContext): StructureInstanceData?

}