package github.kasuminova.prototypemachinery.integration.jei.builtin.decorator

import com.cleanroommc.modularui.drawable.GuiTextures
import com.cleanroommc.modularui.widgets.ProgressWidget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.decorator.PMJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import java.util.Locale

/**
 * Built-in JEI decorator: an animated progress arrow/cycle.
 *
 * Data keys (all optional):
 * - width: Int (default 20)
 * - height: Int (default 20)
 * - direction: String (RIGHT/LEFT/UP/DOWN/CIRCULAR_CW, default RIGHT)
 * - style: String ("arrow" or "cycle", default "arrow")
 * - cycleTicks: Int (animation period in ticks, default ctx.recipe.durationTicks)
 */
public object ProgressArrowJeiDecorator : PMJeiDecorator {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "decorator/progress")

    override fun buildWidgets(
        ctx: JeiRecipeContext,
        x: Int,
        y: Int,
        data: Map<String, Any>,
        out: PMJeiWidgetCollector,
    ) {
        val w = (data["width"] as? Number)?.toInt() ?: 20
        val h = (data["height"] as? Number)?.toInt() ?: 20

        val direction = parseDirection(data["direction"] as? String)

        val style = (data["style"] as? String)?.trim()?.lowercase(Locale.ROOT) ?: "arrow"
        val texture = when (style) {
            "cycle" -> GuiTextures.PROGRESS_CYCLE
            else -> GuiTextures.PROGRESS_ARROW
        }

        val periodTicks = ((data["cycleTicks"] as? Number)?.toInt() ?: ctx.recipe.durationTicks).coerceAtLeast(1)

        val imageSize = when (direction) {
            ProgressWidget.Direction.UP, ProgressWidget.Direction.DOWN -> h
            else -> w
        }.coerceAtLeast(1)

        val widget = ProgressWidget()
            .pos(x, y)
            .size(w, h)
            .direction(direction)
            // GuiTextures.PROGRESS_* is 20x40, with empty/full stacked vertically.
            .texture(texture, imageSize)
            .progress {
                val world = Minecraft.getMinecraft().world
                if (world == null) {
                    0.0
                } else {
                    (world.totalWorldTime % periodTicks).toDouble() / periodTicks.toDouble()
                }
            }

        out.add(widget)
    }

    private fun parseDirection(raw: String?): ProgressWidget.Direction {
        return when (raw?.trim()?.uppercase(Locale.ROOT)) {
            "LEFT" -> ProgressWidget.Direction.LEFT
            "RIGHT" -> ProgressWidget.Direction.RIGHT
            "UP" -> ProgressWidget.Direction.UP
            "DOWN" -> ProgressWidget.Direction.DOWN
            "CIRCULAR_CW" -> ProgressWidget.Direction.CIRCULAR_CW
            else -> ProgressWidget.Direction.RIGHT
        }
    }
}
