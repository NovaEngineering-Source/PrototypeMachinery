package github.kasuminova.prototypemachinery.impl.recipe.requirement.type

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system.ParallelismRequirementSystem
import net.minecraft.util.ResourceLocation

public class ParallelismRequirementType : RecipeRequirementType<ParallelismRequirementComponent> {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "parallelism")

    override val system: RecipeRequirementSystem<ParallelismRequirementComponent> = ParallelismRequirementSystem
}
