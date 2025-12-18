package github.kasuminova.prototypemachinery.integration.jei.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for mapping [github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind]
 * to JEI ingredient types & group init/set logic.
 */
public object JeiIngredientKindRegistry {

    private val handlers: MutableMap<ResourceLocation, PMJeiIngredientKindHandler<*>> = ConcurrentHashMap()

    public fun clear() {
        handlers.clear()
    }

    public fun snapshot(): Map<ResourceLocation, PMJeiIngredientKindHandler<*>> {
        return LinkedHashMap(handlers)
    }

    public fun register(handler: PMJeiIngredientKindHandler<*>, replace: Boolean = true) {
        val id = handler.kindId
        if (!replace && handlers.containsKey(id)) {
            PrototypeMachinery.logger.warn(
                "JEI ingredient kind handler already registered for kindId '$id'. Skipping because replace=false."
            )
            return
        }

        val prev = handlers.put(id, handler)
        if (prev != null && prev !== handler) {
            PrototypeMachinery.logger.info(
                "JEI ingredient kind handler replaced for kindId '$id': ${prev::class.java.name} -> ${handler::class.java.name}"
            )
        }
    }

    public fun get(kindId: ResourceLocation): PMJeiIngredientKindHandler<*>? {
        return handlers[kindId]
    }

    public fun has(kindId: ResourceLocation): Boolean {
        return handlers.containsKey(kindId)
    }
}
