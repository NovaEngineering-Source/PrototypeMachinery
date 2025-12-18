package github.kasuminova.prototypemachinery.integration.jei.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * JEI requirement renderer registry.
 *
 * Keyed by [RecipeRequirementType.id] for stability across reloads.
 */
public object JeiRequirementRendererRegistry {

    private val renderers: MutableMap<ResourceLocation, PMJeiRequirementRenderer<*>> = ConcurrentHashMap()

    public fun clear() {
        renderers.clear()
    }

    public fun snapshot(): Map<ResourceLocation, PMJeiRequirementRenderer<*>> {
        // Copy to ensure callers cannot mutate internal state.
        return LinkedHashMap(renderers)
    }

    public fun <C : RecipeRequirementComponent> register(
        type: RecipeRequirementType<C>,
        renderer: PMJeiRequirementRenderer<C>,
        replace: Boolean = true,
    ) {
        val id = type.id
        if (!replace && renderers.containsKey(id)) {
            PrototypeMachinery.logger.warn(
                "JEI requirement renderer already registered for type '${id}'. Skipping because replace=false."
            )
            return
        }

        val prev = renderers.put(id, renderer)
        if (prev != null && prev !== renderer) {
            PrototypeMachinery.logger.info(
                "JEI requirement renderer replaced for type '${id}': ${prev::class.java.name} -> ${renderer::class.java.name}"
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    public fun <C : RecipeRequirementComponent> get(type: RecipeRequirementType<C>): PMJeiRequirementRenderer<C>? {
        return renderers[type.id] as? PMJeiRequirementRenderer<C>
    }

    public fun has(type: RecipeRequirementType<*>): Boolean {
        return renderers.containsKey(type.id)
    }
}
