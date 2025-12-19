package github.kasuminova.prototypemachinery.integration.jei.builtin

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.ProgressArrowJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.ProgressModuleJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.RecipeDurationTextJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.EnergyKindHandler
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.EnergyNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.VanillaFluidKindHandler
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.VanillaFluidNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.VanillaItemKindHandler
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.VanillaItemNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.EnergyRequirementJeiRenderer
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.FluidRequirementJeiRenderer
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.ItemRequirementJeiRenderer
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.ParallelismRequirementJeiRenderer
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiDecoratorRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiIngredientKindRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiNodeIngredientProviderRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiRequirementRendererRegistry

/**
 * Registers built-in JEI renderers/decorators/layout helpers.
 */
public object PMJeiBuiltins {

    @Volatile
    private var initialized: Boolean = false

    public fun ensureRegistered() {
        if (initialized) return
        initialized = true

        // Requirement renderers
        JeiRequirementRendererRegistry.register(RecipeRequirementTypes.ITEM, ItemRequirementJeiRenderer)
        JeiRequirementRendererRegistry.register(RecipeRequirementTypes.FLUID, FluidRequirementJeiRenderer)
        JeiRequirementRendererRegistry.register(RecipeRequirementTypes.ENERGY, EnergyRequirementJeiRenderer)
        JeiRequirementRendererRegistry.register(RecipeRequirementTypes.PARALLELISM, ParallelismRequirementJeiRenderer)

        // Ingredient kinds (JEI indexing + group init/set)
        JeiIngredientKindRegistry.register(VanillaItemKindHandler)
        JeiIngredientKindRegistry.register(VanillaFluidKindHandler)
        JeiIngredientKindRegistry.register(EnergyKindHandler)

        // Node -> displayed ingredient values
        JeiNodeIngredientProviderRegistry.register(RecipeRequirementTypes.ITEM, VanillaItemNodeIngredientProvider)
        JeiNodeIngredientProviderRegistry.register(RecipeRequirementTypes.FLUID, VanillaFluidNodeIngredientProvider)
        JeiNodeIngredientProviderRegistry.register(RecipeRequirementTypes.ENERGY, EnergyNodeIngredientProvider)

        // Decorators
        JeiDecoratorRegistry.register(ProgressArrowJeiDecorator)
        JeiDecoratorRegistry.register(ProgressModuleJeiDecorator)
        JeiDecoratorRegistry.register(RecipeDurationTextJeiDecorator)
    }
}
