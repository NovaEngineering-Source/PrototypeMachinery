package github.kasuminova.prototypemachinery.api.recipe.index

import github.kasuminova.prototypemachinery.impl.MachineInstanceImpl
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe

/**
 * Represents an index for a specific type of requirement (e.g., Item, Fluid, Energy).
 * Used to quickly filter recipes based on the current state of the machine.
 */
public interface RequirementIndex {
    /**
     * Looks up potential recipes based on the machine's current state for this requirement type.
     *
     * @param machine The machine instance to check.
     * @return A set of potential recipes, or null if this index cannot filter any recipes (e.g., no relevant inputs).
     *         Returning an empty set means no recipes match the current state.
     */
    public fun lookup(machine: MachineInstanceImpl): Set<MachineRecipe>?
}
