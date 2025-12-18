package github.kasuminova.prototypemachinery.impl.recipe

import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import net.minecraft.util.ResourceLocation

/**
 * A minimal in-memory [MachineRecipe] implementation intended for script-driven registration.
 */
public data class SimpleMachineRecipe(
    override val id: String,
    override val durationTicks: Int,
    override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>>,
    override val recipeGroups: Set<ResourceLocation> = emptySet(),
) : MachineRecipe
