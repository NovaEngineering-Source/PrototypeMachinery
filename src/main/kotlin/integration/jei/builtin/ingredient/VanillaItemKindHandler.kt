package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IIngredientType
import net.minecraft.item.ItemStack

public object VanillaItemKindHandler : PMJeiIngredientKindHandler<ItemStack> {

    override val kind: JeiSlotKind = JeiSlotKinds.ITEM

    override val ingredientType: IIngredientType<ItemStack> = VanillaTypes.ITEM

    override fun init(recipeLayout: IRecipeLayout, guiHelper: IGuiHelper, slot: JeiSlot, node: PMJeiRequirementNode<*>?) {
        recipeLayout.itemStacks.init(slot.index, slot.role == github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole.INPUT, slot.x, slot.y)
    }

    override fun set(recipeLayout: IRecipeLayout, slot: JeiSlot, values: List<ItemStack>) {
        recipeLayout.itemStacks.set(slot.index, values)
    }
}
