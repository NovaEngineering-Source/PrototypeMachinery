package github.kasuminova.prototypemachinery.api.machine

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import net.minecraft.util.ResourceLocation

public interface MachineType {

    public val id: ResourceLocation

    public val name: String

    public val structure: MachineStructure

}