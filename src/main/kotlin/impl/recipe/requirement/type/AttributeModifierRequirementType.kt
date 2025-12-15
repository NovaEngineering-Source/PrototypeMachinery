package github.kasuminova.prototypemachinery.impl.recipe.requirement.type

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.AttributeModifierRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system.AttributeModifierRequirementSystem
import net.minecraft.util.ResourceLocation

public class AttributeModifierRequirementType : RecipeRequirementType<AttributeModifierRequirementComponent> {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "attribute_modifier")

    override val system: RecipeRequirementSystem<AttributeModifierRequirementComponent> = AttributeModifierRequirementSystem
}
