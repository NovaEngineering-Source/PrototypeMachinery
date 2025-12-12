package github.kasuminova.prototypemachinery.impl.machine

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.MachineTypeRegistry
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Default implementation of MachineTypeRegistry.
 * 
 * This implementation uses a ConcurrentHashMap for thread-safe registration.
 */
public object MachineTypeRegistryImpl : MachineTypeRegistry {

    private val registry: MutableMap<ResourceLocation, MachineType> = ConcurrentHashMap()

    override fun register(machineType: MachineType) {
        val id = machineType.id
        if (registry.containsKey(id)) {
            throw IllegalArgumentException("Machine type with ID $id is already registered")
        }
        registry[id] = machineType
    }

    override operator fun get(id: ResourceLocation): MachineType? = registry[id]

    override fun contains(id: ResourceLocation): Boolean = registry.containsKey(id)

    override fun all(): Collection<MachineType> = registry.values.toList()

    override fun allIds(): Set<ResourceLocation> = registry.keys.toSet()

}
