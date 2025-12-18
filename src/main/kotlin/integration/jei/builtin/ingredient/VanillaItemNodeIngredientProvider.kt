package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.ItemRequirementJeiRenderer
import net.minecraft.item.ItemStack

public object VanillaItemNodeIngredientProvider : PMJeiNodeIngredientProvider<ItemRequirementComponent, ItemStack> {

    override val type: github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType<ItemRequirementComponent> = RecipeRequirementTypes.ITEM

    override val kind: JeiSlotKind = JeiSlotKinds.ITEM

    override fun getDisplayed(ctx: JeiRecipeContext, node: PMJeiRequirementNode<ItemRequirementComponent>): List<ItemStack> {
        return ItemRequirementJeiRenderer.getDisplayedStacks(node)
    }
}
