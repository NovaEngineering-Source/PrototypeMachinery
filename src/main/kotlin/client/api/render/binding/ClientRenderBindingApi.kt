package github.kasuminova.prototypemachinery.client.api.render.binding

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.client.impl.render.binding.ClientRenderBindingRegistryImpl
import net.minecraft.util.ResourceLocation

/**
 * Public entry point for registering client-side render bindings.
 *
 * This is intentionally tiny and declarative: the implementation lives in `client.impl`.
 */
public object ClientRenderBindingApi {

    /** Clear all registered render bindings (useful for script reload). */
    public fun clearAll() {
        ClientRenderBindingRegistryImpl.clearAll()
    }

    /** Bind a Gecko model to a machine type id (e.g. `prototypemachinery:my_machine`). */
    public fun bindGeckoToMachineType(machineTypeId: ResourceLocation, binding: GeckoModelBinding) {
        ClientRenderBindingRegistryImpl.bindGeckoToMachineType(machineTypeId, binding)
    }

    /** Bind a Gecko model to a machine component type. */
    public fun bindGeckoToComponentType(componentType: MachineComponentType<*>, binding: GeckoModelBinding) {
        ClientRenderBindingRegistryImpl.bindGeckoToComponentType(componentType.id, binding)
    }

    /** Bind a Gecko model to a machine component type id. */
    public fun bindGeckoToComponentType(componentTypeId: ResourceLocation, binding: GeckoModelBinding) {
        ClientRenderBindingRegistryImpl.bindGeckoToComponentType(componentTypeId, binding)
    }

    /**
     * Bind a Gecko model to a specific structure id inside a machine type.
     *
     * This enables nested/sub-structure rendering and allows more fine-grained render task caching.
     */
    public fun bindGeckoToStructure(machineTypeId: ResourceLocation, structureId: String, binding: GeckoStructureBinding) {
        ClientRenderBindingRegistryImpl.bindGeckoToStructure(machineTypeId, structureId, binding)
    }

    /** Convenience overload for simple bindings. */
    public fun bindGeckoToStructure(
        machineTypeId: ResourceLocation,
        structureId: String,
        model: GeckoModelBinding,
        sliceRenderMode: SliceRenderMode = SliceRenderMode.STRUCTURE_ONLY,
    ) {
        bindGeckoToStructure(machineTypeId, structureId, GeckoStructureBinding(model, sliceRenderMode))
    }
}
