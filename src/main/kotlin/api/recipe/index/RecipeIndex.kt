package github.kasuminova.prototypemachinery.api.recipe.index

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe

/**
 * Holds all requirement indices for a specific MachineType.
 */
public class RecipeIndex(
    private val indices: List<RequirementIndex>
) {
    /**
     * Variant of [lookup] that returns `null` when ALL requirement indices have no opinion (all return `null`).
     *
     * This is useful for callers that want to fall back to a broader scan when indexing cannot contribute
     * (e.g. machine has no enumerable ports for indexed requirement types).
     */
    public fun lookupOrNull(machine: MachineInstance): Set<MachineRecipe>? {
        var potentialRecipes: MutableSet<MachineRecipe>? = null

        for (index in indices) {
            val matches = index.lookup(machine)

            // If an index returns null, it means it has no opinion (e.g., no inputs of that type).
            // We skip it.
            if (matches == null) continue

            if (potentialRecipes == null) {
                // First valid result becomes the base set
                // Copy since we'll mutate it via retainAll.
                potentialRecipes = matches.toMutableSet()
            } else {
                // Intersect with existing results
                potentialRecipes.retainAll(matches)
            }

            // Optimization: If intersection becomes empty, we can stop early
            if (potentialRecipes.isEmpty()) {
                return emptySet()
            }
        }

        return potentialRecipes
    }

    /**
     * Finds potential recipes by intersecting the results of all requirement indices.
     *
     * @param machine The machine instance.
     * @return A set of recipes that satisfy ALL indexed requirements.
     */
    public fun lookup(machine: MachineInstance): Set<MachineRecipe> {
        return lookupOrNull(machine) ?: emptySet()
    }
}
