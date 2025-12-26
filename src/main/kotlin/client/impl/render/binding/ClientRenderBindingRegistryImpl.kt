package github.kasuminova.prototypemachinery.client.impl.render.binding

import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoStructureBinding
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.util.ResourceLocation

/**
 * Internal storage for render bindings.
 */
internal object ClientRenderBindingRegistryImpl {

    private val machineTypeBindings: MutableMap<ResourceLocation, GeckoModelBinding> = Object2ObjectOpenHashMap()
    private val componentTypeBindings: MutableMap<ResourceLocation, GeckoModelBinding> = Object2ObjectOpenHashMap()

    // machineTypeId -> (structureId -> binding)
    private val structureBindings: MutableMap<ResourceLocation, MutableMap<String, GeckoStructureBinding>> = Object2ObjectOpenHashMap()

    internal fun bindGeckoToMachineType(machineTypeId: ResourceLocation, binding: GeckoModelBinding) {
        machineTypeBindings[machineTypeId] = binding
    }

    internal fun bindGeckoToComponentType(componentTypeId: ResourceLocation, binding: GeckoModelBinding) {
        componentTypeBindings[componentTypeId] = binding
    }

    internal fun bindGeckoToStructure(machineTypeId: ResourceLocation, structureId: String, binding: GeckoStructureBinding) {
        val map = structureBindings.getOrPut(machineTypeId) { Object2ObjectOpenHashMap() }
        map[structureId] = binding
    }

    internal fun getMachineBinding(machineTypeId: ResourceLocation): GeckoModelBinding? = machineTypeBindings[machineTypeId]

    internal fun getComponentBindings(): Map<ResourceLocation, GeckoModelBinding> = componentTypeBindings

    internal fun getStructureBindings(machineTypeId: ResourceLocation): Map<String, GeckoStructureBinding> =
        structureBindings[machineTypeId].orEmpty()

    internal fun getStructureBinding(machineTypeId: ResourceLocation, structureId: String): GeckoStructureBinding? =
        structureBindings[machineTypeId]?.get(structureId)

    internal fun clearAll() {
        machineTypeBindings.clear()
        componentTypeBindings.clear()
        structureBindings.clear()

        // Binding changes should reflect immediately after reload.
        RenderTaskCache.clearAll()
    }
}
