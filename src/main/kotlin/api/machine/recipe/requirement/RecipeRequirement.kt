package github.kasuminova.prototypemachinery.api.machine.recipe.requirement

public interface RecipeRequirement {

    public val id: String

    public val type: RecipeRequirementType

    public val data: RecipeRequirementData

}