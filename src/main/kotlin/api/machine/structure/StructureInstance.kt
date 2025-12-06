package github.kasuminova.prototypemachinery.api.machine.structure

public interface StructureInstance {

    public val structure: MachineStructure

    public val data: StructureInstanceData

    public val children: Map<MachineStructure, List<StructureInstance>>

}