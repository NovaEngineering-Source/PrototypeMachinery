package github.kasuminova.prototypemachinery.client.buildinstrument.widget

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.common.buildinstrument.BuildInstrumentNbt.TaskState
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.awt.Color

internal class BuildInstrumentProgressBarWidget(
    private val stateProvider: () -> TaskState,
    private val doneProvider: () -> Int,
    private val totalProvider: () -> Int,
    private val buildColor: Color,
    private val buildTailColor: Color,
    private val pauseColor: Color,
    private val pauseTailColor: Color,
    private val disColor: Color,
    private val disTailColor: Color
) : Widget<BuildInstrumentProgressBarWidget>() {

    @SideOnly(Side.CLIENT)
    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val total = totalProvider().coerceAtLeast(0)
        val done = doneProvider().coerceIn(0, if (total == 0) 0 else total)

        val frac = if (total <= 0) 0.0 else (done.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)

        val (fillC, tailC) = when (stateProvider()) {
            TaskState.BUILDING, TaskState.IDLE -> buildColor to buildTailColor
            TaskState.PAUSED_BUILDING, TaskState.PAUSED_DISASSEMBLING -> pauseColor to pauseTailColor
            TaskState.DISASSEMBLING -> disColor to disTailColor
        }

        // Spec: W:1=0%, W:159=100% for a 160px bar.
        val minW = 1
        val maxW = 159
        val fillW = if (total <= 0) minW else (minW + (frac * (maxW - minW)).toInt()).coerceIn(minW, maxW)

        fun argb(c: Color): Int = (0xFF shl 24) or (c.rgb and 0xFFFFFF)

        // Draw filled part.
        com.cleanroommc.modularui.drawable.GuiDraw.drawRect(0f, 0f, fillW.toFloat(), area.h().toFloat(), argb(fillC))

        // Draw tail (1px) at the end of fill.
        val tailX = (fillW - 1).coerceAtLeast(0)
        com.cleanroommc.modularui.drawable.GuiDraw.drawRect(tailX.toFloat(), 0f, 1f, area.h().toFloat(), argb(tailC))

        super.draw(context, widgetTheme)
    }
}
