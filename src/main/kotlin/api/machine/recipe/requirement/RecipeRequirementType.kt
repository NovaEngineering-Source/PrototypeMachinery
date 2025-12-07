package github.kasuminova.prototypemachinery.api.machine.recipe.requirement

import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem
import net.minecraft.util.ResourceLocation

public interface RecipeRequirementType<C : RecipeRequirementComponent> {

    public val id: ResourceLocation

    public val system: RecipeRequirementSystem<C>

}