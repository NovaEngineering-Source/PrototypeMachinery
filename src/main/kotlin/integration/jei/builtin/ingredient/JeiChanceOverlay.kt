package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import com.cleanroommc.modularui.drawable.GuiDraw
import mezz.jei.api.gui.IDrawable
import mezz.jei.api.ingredients.IIngredientRenderer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.FontRenderer
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import java.util.Locale

internal object JeiChanceOverlay {

    // Slightly warm gold; matches existing tooltip tone.
    private const val DEFAULT_COLOR: Int = 0xFFEAD76C.toInt()
    private const val DEFAULT_SCALE: Float = 0.6f

    fun buildLabel(chancePercent: Double?, hasChanceAttribute: Boolean): String? {
        val chance = chancePercent ?: return null
        if (!chance.isFinite()) return null

        // Avoid noisy "100%" unless it's attribute-driven.
        if (chance == 100.0 && !hasChanceAttribute) return null

        val pct = formatPercent(chance)
        return buildString {
            append(pct)
            append('%')
            if (hasChanceAttribute) append('*')
        }
    }

    private fun formatPercent(v: Double): String {
        val rounded = kotlin.math.round(v * 100.0) / 100.0
        val asLong = rounded.toLong()
        return if (rounded == asLong.toDouble()) {
            asLong.toString()
        } else {
            String.format(Locale.ROOT, "%.2f", rounded).trimEnd('0').trimEnd('.')
        }
    }

    /** Draws `base` then `top` so [top] is always above. */
    class CompositeDrawable(
        private val base: IDrawable,
        private val top: IDrawable,
    ) : IDrawable {
        override fun getWidth(): Int = base.width
        override fun getHeight(): Int = base.height

        override fun draw(minecraft: Minecraft, xOffset: Int, yOffset: Int) {
            base.draw(minecraft, xOffset, yOffset)
            top.draw(minecraft, xOffset, yOffset)
        }
    }

    /** An IDrawable that draws a small label on the top-left corner. */
    class ChanceLabelDrawable(
        private val label: String,
        private val width: Int,
        private val height: Int,
        private val color: Int = DEFAULT_COLOR,
        private val scale: Float = DEFAULT_SCALE,
        private val shadow: Boolean = true,
    ) : IDrawable {

        override fun getWidth(): Int = width
        override fun getHeight(): Int = height

        override fun draw(minecraft: Minecraft, xOffset: Int, yOffset: Int) {
            // We want this above ingredient + overlay.
            GlStateManager.disableDepth()
            GlStateManager.disableLighting()
            GuiDraw.drawText(label, (xOffset + 1).toFloat(), (yOffset + 1).toFloat(), scale, color, shadow)

            // GuiDraw.drawText() calls Platform.setupDrawFont() which disables blending.
            // JEI relies on blending for tooltips and UI elements, so restore a sane GUI state.
            GlStateManager.enableTexture2D()
            GlStateManager.enableAlpha()
            GlStateManager.enableBlend()
            GlStateManager.disableLighting()
            GlStateManager.enableDepth()
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        }
    }

    /**
     * Custom item renderer that draws the stack then overlays the chance label on top.
     *
     * This guarantees the label is above the item itself (and not affected by our ModularUI layer ordering).
     */
    class ChanceOverlayItemRenderer(
        private val label: String,
        private val color: Int = DEFAULT_COLOR,
        private val scale: Float = DEFAULT_SCALE,
        private val shadow: Boolean = true,
    ) : IIngredientRenderer<ItemStack> {

        override fun getTooltip(minecraft: Minecraft, ingredient: ItemStack, tooltipFlag: ITooltipFlag): MutableList<String> {
            if (ingredient.isEmpty) return mutableListOf()
            val player = minecraft.player ?: return mutableListOf()
            // Delegate to vanilla ItemStack tooltip so JEI can render its tooltip box and then apply callbacks.
            return ingredient.getTooltip(player, tooltipFlag).toMutableList()
        }

        override fun render(minecraft: Minecraft, xPosition: Int, yPosition: Int, ingredient: ItemStack?) {
            if (ingredient != null && !ingredient.isEmpty) {
                GlStateManager.enableDepth()
                RenderHelper.enableGUIStandardItemLighting()
                val font = getFontRenderer(minecraft, ingredient)
                minecraft.renderItem.renderItemAndEffectIntoGUI(null, ingredient, xPosition, yPosition)
                minecraft.renderItem.renderItemOverlayIntoGUI(font, ingredient, xPosition, yPosition, null)
                GlStateManager.disableBlend()
                RenderHelper.disableStandardItemLighting()
            }

            // Always draw label (even if ingredient is null) so the slot meaning is visible.
            GlStateManager.disableDepth()
            GlStateManager.disableLighting()
            GuiDraw.drawText(label, (xPosition + 1).toFloat(), (yPosition + 1).toFloat(), scale, color, shadow)

            // Restore a sane GUI state for JEI (blending is required for tooltips/buttons).
            GlStateManager.enableTexture2D()
            GlStateManager.enableAlpha()
            GlStateManager.enableBlend()
            GlStateManager.disableLighting()
            GlStateManager.enableDepth()
            GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)
        }

        override fun getFontRenderer(minecraft: Minecraft, ingredient: ItemStack): FontRenderer {
            return ingredient.item.getFontRenderer(ingredient) ?: minecraft.fontRenderer
        }
    }
}
