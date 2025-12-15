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
     * Finds potential recipes by intersecting the results of all requirement indices.
     *
     * @param machine The machine instance.
     * @return A set of recipes that satisfy ALL indexed requirements.
     */
    public fun lookup(machine: MachineInstance): Set<MachineRecipe> {
        var potentialRecipes: MutableSet<MachineRecipe>? = null

        for (index in indices) {
            val matches = index.lookup(machine)

            // If an index returns null, it means it has no opinion (e.g., no inputs of that type).
            // We skip it.
            if (matches == null) continue

            if (potentialRecipes == null) {
                // First valid result becomes the base set
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

        // If no indices returned anything (all null), it implies no indexed requirements exist.
        // In this case, we should probably return ALL recipes or handle it upstream.
        // However, the contract says "potential recipes".
        // If potentialRecipes is null here, it means we have no filters.
        // The caller should probably fall back to full scan or we return empty if we assume indices cover everything.
        // But usually, indices are for *inputs*. If a machine has no inputs, it might run everything?
        // For safety, if null, we return empty set to force a fallback or indicate no match found via index.
        // Better yet, the caller should check if RecipeIndex exists.

        return potentialRecipes ?: emptySet()
    }
}
