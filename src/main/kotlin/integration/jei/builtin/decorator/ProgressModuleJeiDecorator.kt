package github.kasuminova.prototypemachinery.integration.jei.builtin.decorator

import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import com.cleanroommc.modularui.widgets.ProgressWidget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.decorator.PMJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiIcons
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import java.util.Locale

/**
 * Built-in JEI decorator for the new progress module icons.
 *
 * Data keys:
 * - type: String (e.g., "compress", "heat", "right", default "right")
 * - direction: String (RIGHT/LEFT/UP/DOWN, default RIGHT)
 * - cycleTicks: Int (animation period, default recipe duration)
 */
public object ProgressModuleJeiDecorator : PMJeiDecorator {

    override val id: ResourceLocation = ResourceLocation(PrototypeMachinery.MOD_ID, "decorator/progress_module")

    override fun buildWidgets(
        ctx: JeiRecipeContext,
        x: Int,
        y: Int,
        data: Map<String, Any>,
        out: PMJeiWidgetCollector
    ) {
        val type = (data["type"] as? String)?.lowercase(Locale.ROOT) ?: "right"
        val directionStr = (data["direction"] as? String)?.uppercase(Locale.ROOT) ?: "RIGHT"
        val direction = parseDirection(directionStr)

        val periodTicks = ((data["cycleTicks"] as? Number)?.toInt() ?: ctx.recipe.durationTicks).coerceAtLeast(1)

        out.add(ProgressModuleWidget(type, direction, periodTicks).pos(x, y))
    }

    private fun parseDirection(raw: String): ProgressWidget.Direction {
        return when (raw) {
            "LEFT" -> ProgressWidget.Direction.LEFT
            "RIGHT" -> ProgressWidget.Direction.RIGHT
            "UP" -> ProgressWidget.Direction.UP
            "DOWN" -> ProgressWidget.Direction.DOWN
            else -> ProgressWidget.Direction.RIGHT
        }
    }

    private class ProgressModuleWidget(
        private val type: String,
        private val direction: ProgressWidget.Direction,
        private val periodTicks: Int
    ) : Widget<ProgressModuleWidget>() {

        private val baseTex: ResourceLocation
        private val runTex: ResourceLocation

        init {
            // progress_module uses flat filenames like: right_base.png / right_run.png
            // Heat/Cool are special: cool swaps base/run per docs.
            val (baseName, runName) = when (type) {
                "cool" -> "heat_run_0.png" to "heat_base.png"
                "heat" -> "heat_base.png" to "heat_run_1.png"
                else -> "${type}_base.png" to "${type}_run.png"
            }
            baseTex = PMJeiIcons.tex("progress_module/$baseName")
            runTex = PMJeiIcons.tex("progress_module/$runName")

            // All progress_module icons are 18x18.
            size(18, 18)
        }

        override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
            val mc = Minecraft.getMinecraft()
            val progress = if (periodTicks <= 0) 0.0 else {
                val world = mc.world
                if (world == null) 0.0 else (world.totalWorldTime % periodTicks).toDouble() / periodTicks.toDouble()
            }

            // 1. Draw base
            GuiDraw.drawTexture(baseTex, 0f, 0f, area.width.toFloat(), area.height.toFloat(), 0f, 0f, 1f, 1f)

            // 2. Draw run layer (clipped by progress)
            val w = area.width.toFloat()
            val h = area.height.toFloat()

            when (direction) {
                ProgressWidget.Direction.RIGHT -> {
                    val drawW = (w * progress).toFloat()
                    if (drawW > 0) {
                        GuiDraw.drawTexture(runTex, 0f, 0f, drawW, h, 0f, 0f, progress.toFloat(), 1f)
                    }
                }
                ProgressWidget.Direction.LEFT -> {
                    val drawW = (w * progress).toFloat()
                    if (drawW > 0) {
                        GuiDraw.drawTexture(runTex, w - drawW, 0f, w, h, (1.0 - progress).toFloat(), 0f, 1f, 1f)
                    }
                }
                ProgressWidget.Direction.DOWN -> {
                    val drawH = (h * progress).toFloat()
                    if (drawH > 0) {
                        GuiDraw.drawTexture(runTex, 0f, 0f, w, drawH, 0f, 0f, 1f, progress.toFloat())
                    }
                }
                ProgressWidget.Direction.UP -> {
                    val drawH = (h * progress).toFloat()
                    if (drawH > 0) {
                        GuiDraw.drawTexture(runTex, 0f, h - drawH, w, h, 0f, (1.0 - progress).toFloat(), 1f, 1f)
                    }
                }
                else -> {
                    GuiDraw.drawTexture(runTex, 0f, 0f, w, h, 0f, 0f, 1f, 1f)
                }
            }
        }
    }
}
