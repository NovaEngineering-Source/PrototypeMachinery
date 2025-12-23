package github.kasuminova.prototypemachinery.impl.recipe.scanning

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.scanning.RecipeParallelismConstraintRegistry
import github.kasuminova.prototypemachinery.impl.recipe.scanning.constraint.EnergyRecipeParallelismConstraint
import github.kasuminova.prototypemachinery.impl.recipe.scanning.constraint.FluidRecipeParallelismConstraint
import github.kasuminova.prototypemachinery.impl.recipe.scanning.constraint.ItemRecipeParallelismConstraint

/**
 * Registers default scan-time parallelism constraints for built-in requirement types.
 */
public object DefaultRecipeParallelismConstraints {

    @JvmStatic
    public fun registerAll() {
        // Idempotent by nature: later registrations overwrite the previous entry.
        RecipeParallelismConstraintRegistry.register(ItemRecipeParallelismConstraint(RecipeRequirementTypes.ITEM.id))
        RecipeParallelismConstraintRegistry.register(FluidRecipeParallelismConstraint(RecipeRequirementTypes.FLUID.id))
        RecipeParallelismConstraintRegistry.register(EnergyRecipeParallelismConstraint(RecipeRequirementTypes.ENERGY.id))
    }
}
