package github.kasuminova.prototypemachinery.api.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem

/**
 * # RecipeRequirementRegistry
 * # 配方需求注册表
 * 
 * (Placeholder for the actual registry)
 */
public object RecipeRequirementRegistry {
    private val systems = mutableMapOf<RecipeRequirementType<*>, RecipeRequirementSystem<*>>()

    public fun <C : RecipeRequirementComponent> register(type: RecipeRequirementType<C>, system: RecipeRequirementSystem<C>) {
        systems[type] = system
    }

    public fun <C : RecipeRequirementComponent> getSystem(type: RecipeRequirementType<C>): RecipeRequirementSystem<C>? {
        @Suppress("UNCHECKED_CAST")
        return systems[type] as? RecipeRequirementSystem<C>
    }
}