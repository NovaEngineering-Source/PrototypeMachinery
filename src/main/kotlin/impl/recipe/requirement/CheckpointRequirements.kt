package github.kasuminova.prototypemachinery.impl.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.CheckpointRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.CheckpointRequirementType

public object CheckpointRequirements {
    @JvmField
    public val CHECKPOINT_TYPE: RecipeRequirementType<CheckpointRequirementComponent> =
        RecipeRequirementTypes.register(CheckpointRequirementType())
}
