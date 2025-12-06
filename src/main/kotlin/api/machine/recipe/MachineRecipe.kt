package github.kasuminova.prototypemachinery.api.machine.recipe

import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirement
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType

public interface MachineRecipe {

    public val id: String

    public val requirements: Map<RecipeRequirementType, List<RecipeRequirement>>

}