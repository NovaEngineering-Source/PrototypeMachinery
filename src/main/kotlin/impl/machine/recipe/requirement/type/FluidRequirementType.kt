package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.type

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.system.FluidRequirementSystem
import net.minecraft.util.ResourceLocation

public class FluidRequirementType : RecipeRequirementType<FluidRequirementComponent> {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "fluid")

    override val system: RecipeRequirementSystem<FluidRequirementComponent> = FluidRequirementSystem

}
