package github.kasuminova.prototypemachinery.integration.jei.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.decorator.PMJeiDecorator
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * JEI decorator registry.
 *
 * Keyed by decorator [ResourceLocation] for stability across reloads.
 */
public object JeiDecoratorRegistry {

    private val decorators: MutableMap<ResourceLocation, PMJeiDecorator> = ConcurrentHashMap()

    public fun clear() {
        decorators.clear()
    }

    public fun snapshot(): Map<ResourceLocation, PMJeiDecorator> {
        return LinkedHashMap(decorators)
    }

    public fun register(
        decorator: PMJeiDecorator,
        replace: Boolean = true,
    ) {
        val id = decorator.id
        if (!replace && decorators.containsKey(id)) {
            PrototypeMachinery.logger.warn(
                "JEI decorator already registered for id '$id'. Skipping because replace=false."
            )
            return
        }

        val prev = decorators.put(id, decorator)
        if (prev != null && prev !== decorator) {
            PrototypeMachinery.logger.info(
                "JEI decorator replaced for id '$id': ${prev::class.java.name} -> ${decorator::class.java.name}"
            )
        }
    }

    public fun get(id: ResourceLocation): PMJeiDecorator? {
        return decorators[id]
    }

    public fun has(id: ResourceLocation): Boolean {
        return decorators.containsKey(id)
    }
}
