package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.type

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.system.ItemRequirementSystem
import net.minecraft.util.ResourceLocation

public class ItemRequirementType : RecipeRequirementType<ItemRequirementComponent> {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "item")

    override val system: RecipeRequirementSystem<ItemRequirementComponent> = ItemRequirementSystem

}
