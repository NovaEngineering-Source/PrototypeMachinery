package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.StructureRegistry
import net.minecraft.util.EnumFacing
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of StructureRegistry.
 * 结构注册表的默认实现。
 *
 * Thread-safe with concurrent collections.
 * 使用并发集合实现线程安全。
 */
public object StructureRegistryImpl : StructureRegistry {

    private val structures: MutableMap<String, MachineStructure> = ConcurrentHashMap()
    private val cache: MutableMap<String, MutableMap<StructureOrientation, MachineStructure>> = ConcurrentHashMap()

    override fun register(structure: MachineStructure) {
        val id = structure.id
        if (structures.containsKey(id)) {
            throw IllegalArgumentException("Structure with ID $id is already registered")
        }
        structures[id] = structure
        cache.remove(id)
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
        cache.computeIfAbsent(id) { ConcurrentHashMap() }[orientation] = transformed

        return transformed
    }

    override fun getAll(): Collection<MachineStructure> = structures.values.toList()

    override fun contains(id: String): Boolean = structures.containsKey(id)

}
