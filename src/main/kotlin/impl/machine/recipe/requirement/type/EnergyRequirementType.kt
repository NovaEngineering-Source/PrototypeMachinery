package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.type

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.system.EnergyRequirementSystem
import net.minecraft.util.ResourceLocation

public class EnergyRequirementType : RecipeRequirementType<EnergyRequirementComponent> {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "energy")

    override val system: RecipeRequirementSystem<EnergyRequirementComponent> = EnergyRequirementSystem

}
