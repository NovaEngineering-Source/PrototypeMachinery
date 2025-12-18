package github.kasuminova.prototypemachinery.integration.jei.registry

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that maps requirement [RecipeRequirementType] -> ingredient provider.
 */
public object JeiNodeIngredientProviderRegistry {

    private val providers: MutableMap<ResourceLocation, PMJeiNodeIngredientProvider<*, *>> = ConcurrentHashMap()

    public fun clear() {
        providers.clear()
    }

    public fun snapshot(): Map<ResourceLocation, PMJeiNodeIngredientProvider<*, *>> {
        return LinkedHashMap(providers)
    }

    public fun <C : RecipeRequirementComponent, T : Any> register(
        type: RecipeRequirementType<C>,
        provider: PMJeiNodeIngredientProvider<C, T>,
        replace: Boolean = true,
    ) {
        val id = type.id
        if (!replace && providers.containsKey(id)) {
            PrototypeMachinery.logger.warn(
                "JEI node ingredient provider already registered for type '$id'. Skipping because replace=false."
            )
            return
        }

        val prev = providers.put(id, provider)
        if (prev != null && prev !== provider) {
            PrototypeMachinery.logger.info(
                "JEI node ingredient provider replaced for type '$id': ${prev::class.java.name} -> ${provider::class.java.name}"
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    public fun <C : RecipeRequirementComponent, T : Any> getTyped(type: RecipeRequirementType<C>): PMJeiNodeIngredientProvider<C, T>? {
        return providers[type.id] as? PMJeiNodeIngredientProvider<C, T>
    }

    /**
     * Non-generic lookup for situations where the concrete ingredient value type is unknown.
     *
     * This avoids Kotlin type inference failures in call sites that only have [RecipeRequirementType<*>].
     */
    public fun get(type: RecipeRequirementType<*>): PMJeiNodeIngredientProvider<*, *>? {
        return providers[type.id]
    }

    public fun has(type: RecipeRequirementType<*>): Boolean {
        return providers.containsKey(type.id)
    }
}
