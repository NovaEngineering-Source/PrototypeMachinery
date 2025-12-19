package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.init.Items
import net.minecraft.item.ItemStack

/** Renderer used in JEI ingredient list. Draws a small icon so the entry is discoverable. */
public object EnergyJeiIngredientListRenderer : IIngredientRenderer<EnergyJeiIngredient> {

    override fun render(minecraft: Minecraft, xPosition: Int, yPosition: Int, ingredient: EnergyJeiIngredient?) {
        if (ingredient == null) return

        // Small visual hint in the ingredient list.
        // Use vanilla items to avoid shipping extra textures.
        val stack: ItemStack = when (ingredient.direction) {
            EnergyJeiIngredient.Direction.CONSUME -> ItemStack(Items.REDSTONE)
            EnergyJeiIngredient.Direction.PRODUCE -> ItemStack(Items.GLOWSTONE_DUST)
        }
        minecraft.renderItem.renderItemIntoGUI(stack, xPosition, yPosition)
    }

    override fun getTooltip(minecraft: Minecraft, ingredient: EnergyJeiIngredient, tooltipFlag: ITooltipFlag): MutableList<String> {
        return buildTooltip(ingredient)
    }

    override fun getFontRenderer(minecraft: Minecraft, ingredient: EnergyJeiIngredient): FontRenderer {
        return minecraft.fontRenderer
    }
}

/** Renderer used for recipe slots: provides tooltip + focus, but does not paint over the energy widget. */
public object EnergyJeiIngredientSlotRenderer : IIngredientRenderer<EnergyJeiIngredient> {

    override fun render(minecraft: Minecraft, xPosition: Int, yPosition: Int, ingredient: EnergyJeiIngredient?) {
        // Intentionally no-op: the ModularUI energy widget provides the visuals.
    }

    override fun getTooltip(minecraft: Minecraft, ingredient: EnergyJeiIngredient, tooltipFlag: ITooltipFlag): MutableList<String> {
        return buildTooltip(ingredient)
    }

    override fun getFontRenderer(minecraft: Minecraft, ingredient: EnergyJeiIngredient): FontRenderer {
        return minecraft.fontRenderer
    }
}

private fun buildTooltip(ingredient: EnergyJeiIngredient): MutableList<String> {
    val out = ArrayList<String>(3)
    out += EnergyJeiIngredientHelper.formatDisplayName(ingredient)
    out += EnergyJeiIngredientHelper.formatAmountLine(ingredient)
    return out
}
