package github.kasuminova.prototypemachinery.api.machine.recipe.requirement

public interface RecipeRequirement {

    public val id: String

    public val type: RecipeRequirementType<*>

    public val data: RecipeRequirementData

    /**
     * The priority of this requirement check. Higher values are checked first.
     * Use this to optimize performance by failing fast on cheap or critical requirements.
     */
    public val priority: Int get() = 0

}