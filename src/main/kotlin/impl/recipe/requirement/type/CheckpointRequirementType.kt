package github.kasuminova.prototypemachinery.impl.recipe.requirement.type

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.CheckpointRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system.CheckpointRequirementSystem
import net.minecraft.util.ResourceLocation

public class CheckpointRequirementType : RecipeRequirementType<CheckpointRequirementComponent> {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "checkpoint")

    override val system: RecipeRequirementSystem<CheckpointRequirementComponent> = CheckpointRequirementSystem
}
