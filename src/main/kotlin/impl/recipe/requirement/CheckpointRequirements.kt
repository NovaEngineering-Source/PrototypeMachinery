package github.kasuminova.prototypemachinery.impl.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementRegistry
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.CheckpointRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system.CheckpointRequirementSystem

public object CheckpointRequirements {
    public val CHECKPOINT_TYPE: RecipeRequirementType<CheckpointRequirementComponent> = TODO()

    public fun init() {
        RecipeRequirementRegistry.register(CHECKPOINT_TYPE, CheckpointRequirementSystem)
    }
}
