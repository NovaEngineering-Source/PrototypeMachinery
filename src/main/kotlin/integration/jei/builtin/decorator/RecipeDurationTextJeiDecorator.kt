package github.kasuminova.prototypemachinery.integration.jei.builtin.decorator

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.widgets.TextWidget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.decorator.PMJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import net.minecraft.util.ResourceLocation
import java.util.Locale

/**
 * Built-in JEI decorator: renders recipe duration as text.
 *
 * Data keys (all optional):
 * - template: String (default "{ticks} t ({seconds}s)")
 * - width: Int (default 80)
 * - height: Int (default 10)
 * - align: String (CENTER/LEFT/RIGHT/TOP_LEFT/etc, default CENTER)
 * - scale: Float (default 1.0)
 * - color: Int (ARGB/RGB, default theme)
 * - shadow: Boolean (default theme)
 */
public object RecipeDurationTextJeiDecorator : PMJeiDecorator {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "decorator/recipe_duration")

    override fun buildWidgets(
        ctx: JeiRecipeContext,
        x: Int,
        y: Int,
        data: Map<String, Any>,
        out: PMJeiWidgetCollector,
    ) {
        val ticks = ctx.recipe.durationTicks.coerceAtLeast(1)
        val seconds = ticks.toDouble() / 20.0

        val template = (data["template"] as? String) ?: "{ticks} t ({seconds}s)"
        val text = formatTemplate(template, ticks, seconds)

        val w = (data["width"] as? Number)?.toInt() ?: 80
        val h = (data["height"] as? Number)?.toInt() ?: 10
        val scale = (data["scale"] as? Number)?.toFloat() ?: 1.0f

        val color = (data["color"] as? Number)?.toInt()
        val shadow = (data["shadow"] as? Boolean)

        val alignRaw = (data["align"] as? String) ?: "CENTER"
        val alignment = parseAlignment(alignRaw)

        val widget = TextWidget(IKey.str(text))
            .pos(x, y)
            .size(w, h)
            .alignment(alignment)
            .scale(scale)

        if (color != null) {
            widget.color(color)
        }
        if (shadow != null) {
            widget.shadow(shadow)
        }

        out.add(widget)
    }

    private fun formatTemplate(template: String, ticks: Int, seconds: Double): String {
        val sec2 = String.format(Locale.ROOT, "%.2f", seconds)
        return template
            .replace("{ticks}", ticks.toString())
            .replace("{seconds}", sec2)
    }

    private fun parseAlignment(raw: String): Alignment {
        return when (raw.trim().uppercase(Locale.ROOT)) {
            "LEFT" -> Alignment.CenterLeft
            "CENTER" -> Alignment.Center
            "RIGHT" -> Alignment.CenterRight
            "TOP_LEFT" -> Alignment.TopLeft
            "TOP_CENTER" -> Alignment.TopCenter
            "TOP_RIGHT" -> Alignment.TopRight
            "BOTTOM_LEFT" -> Alignment.BottomLeft
            "BOTTOM_CENTER" -> Alignment.BottomCenter
            "BOTTOM_RIGHT" -> Alignment.BottomRight
            else -> Alignment.Center
        }
    }
}
