package github.kasuminova.prototypemachinery.integration.crafttweaker

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponentType
import github.kasuminova.prototypemachinery.impl.machine.component.type.FactoryRecipeProcessorComponentType
import net.minecraft.util.ResourceLocation

/**
 * Central lookup table for script-facing machine component types.
 *
 * Scripts can refer to these via bracket handlers like:
 * - <pmcomponent:factory_recipe_processor>
 * - <machinecomponent:prototypemachinery:factory_recipe_processor>
 */
public object MachineComponentTypeLookup {

    private val byId: Map<ResourceLocation, MachineComponentType<*>> = linkedMapOf(
        ZSDataComponentType.id to ZSDataComponentType,
        FactoryRecipeProcessorComponentType.id to FactoryRecipeProcessorComponentType,
    )

    @JvmStatic
    public fun get(id: ResourceLocation): MachineComponentType<*>? {
        return byId[id]
    }

    @JvmStatic
    public fun get(id: String): MachineComponentType<*>? {
        return get(ResourceLocation(id))
    }
}
