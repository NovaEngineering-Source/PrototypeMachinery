package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.StructureRegistry
import net.minecraft.util.EnumFacing

public object StructureRegistryImpl : StructureRegistry {

    private val structures = mutableMapOf<String, MachineStructure>()
    private val cache = mutableMapOf<String, MutableMap<StructureOrientation, MachineStructure>>()

    override fun register(structure: MachineStructure) {
        structures[structure.id] = structure
        cache.remove(structure.id)
    }

    override fun get(id: String): MachineStructure? = structures[id]

    override fun get(id: String, orientation: StructureOrientation, horizontalFacing: EnumFacing): MachineStructure? {
        // Check cache first
        cache[id]?.get(orientation)?.let { return it }

        val structure = structures[id] ?: return null

        // Assuming base structure is NORTH/UP
        val base = StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)
        val target = orientation

        val rotation: (EnumFacing) -> EnumFacing = { facing ->
            when (facing) {
                base.front -> target.front
                base.front.opposite -> target.front.opposite
                base.top -> target.top
                base.top.opposite -> target.top.opposite
                base.right -> target.right
                base.right.opposite -> target.right.opposite
                else -> facing
            }
        }

        val transformed = structure.transform(rotation)

        // Cache the result
        cache.computeIfAbsent(id) { HashMap() }[orientation] = transformed

        return transformed
    }

}
