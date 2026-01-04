package github.kasuminova.prototypemachinery.impl.recipe.scanning.constraint

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.scanning.RecipeParallelismConstraint
import net.minecraft.util.ResourceLocation

/**
 * A no-op scan-time parallelism constraint.
 *
 * Used for requirement types that should not participate in scan-time feasibility checks
 * (e.g., PARALLELISM as it is treated as a multiplier/modifier, not a consumable requirement).
 */
public class NoOpRecipeParallelismConstraint(
    override val requirementTypeId: ResourceLocation
) : RecipeParallelismConstraint {

    override fun canSatisfy(
        machine: MachineInstance,
        recipe: MachineRecipe,
        components: List<RecipeRequirementComponent>,
        parallels: Int
    ): Boolean = true
}
