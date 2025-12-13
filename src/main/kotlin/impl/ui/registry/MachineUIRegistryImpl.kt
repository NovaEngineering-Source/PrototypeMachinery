package github.kasuminova.prototypemachinery.impl.ui.registry

import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.api.ui.registry.MachineUIRegistry
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong

public object MachineUIRegistryImpl : MachineUIRegistry {

    private val orderSeq = AtomicLong(0)

    private val byMachineId: MutableMap<ResourceLocation, CopyOnWriteArrayList<MachineUIRegistry.Registration>> =
        ConcurrentHashMap()

    override fun register(machineId: ResourceLocation, definition: WidgetDefinition, priority: Int, owner: String) {
        val list = byMachineId.computeIfAbsent(machineId) { CopyOnWriteArrayList() }
        val reg = MachineUIRegistry.Registration(
            machineId = machineId,
            owner = owner,
            priority = priority,
            order = orderSeq.incrementAndGet(),
            definition = definition
        )
        list.add(reg)
        // Sort: priority desc, then order desc (last wins at same priority)
        list.sortWith(compareByDescending<MachineUIRegistry.Registration> { it.priority }
            .thenByDescending { it.order })
    }

    override fun clear(machineId: ResourceLocation) {
        byMachineId.remove(machineId)
    }

    override fun clearAll() {
        byMachineId.clear()
    }

    override fun resolve(machineId: ResourceLocation): WidgetDefinition? {
        return byMachineId[machineId]?.firstOrNull()?.definition
    }

    override fun list(machineId: ResourceLocation): List<MachineUIRegistry.Registration> {
        return byMachineId[machineId]?.toList() ?: emptyList()
    }
}
