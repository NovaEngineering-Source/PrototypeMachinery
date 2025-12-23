package github.kasuminova.prototypemachinery.api.recipe.requirement.advanced

/**
 * Well-known keys for [github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent.properties].
 *
 * These keys are intentionally string-based to keep ABI compatibility for addons:
 * extending semantics via properties does not change existing constructor signatures.
 */
public object RequirementPropertyKeys {

    /** Double percent, e.g. 50.0 means 50%. May be > 100. */
    public const val CHANCE: String = "chance"

    /** Optional: attribute id (String or ResourceLocation) used as a multiplier to chance. */
    public const val CHANCE_ATTRIBUTE: String = "chanceAttribute"

    /** Optional: fuzzy input groups (List<FuzzyInputGroup<*>>). */
    public const val FUZZY_INPUTS: String = "fuzzy_inputs"

    /** Optional: random output pool (RandomOutputPool<*>). */
    public const val RANDOM_OUTPUTS: String = "random_outputs"

    /** Existing legacy flag: allow voiding when output is full. */
    public const val IGNORE_OUTPUT_FULL: String = "ignore_output_full"
}
