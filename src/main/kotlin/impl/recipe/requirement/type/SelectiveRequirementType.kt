package github.kasuminova.prototypemachinery.impl.recipe.requirement.type

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.SelectiveRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system.SelectiveRequirementSystem
import net.minecraft.util.ResourceLocation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public class SelectiveRequirementType : RecipeRequirementType<SelectiveRequirementComponent> {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "selective")

    override val system: RecipeRequirementSystem<SelectiveRequirementComponent> = SelectiveRequirementSystem
}
