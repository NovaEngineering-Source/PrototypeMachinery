package github.kasuminova.prototypemachinery.api.machine.structure

import net.minecraft.util.EnumFacing

public interface StructureRegistry {

    public fun register(structure: MachineStructure)

    public fun get(id: String): MachineStructure?

    public fun get(id: String, orientation: StructureOrientation, horizontalFacing: EnumFacing): MachineStructure?

}