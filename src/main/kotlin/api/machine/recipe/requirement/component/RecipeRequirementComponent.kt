package github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component

import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType

public interface RecipeRequirementComponent {

    public val type: RecipeRequirementType<*>

}