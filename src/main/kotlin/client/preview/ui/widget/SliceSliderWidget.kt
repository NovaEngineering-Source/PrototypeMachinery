package github.kasuminova.prototypemachinery.client.preview.ui.widget

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.api.widget.Interactable.Result
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.value.IntValue
import com.cleanroommc.modularui.widget.Widget
import kotlin.math.roundToInt

/**
 * Vertical slice slider with a draggable handle.
 *
 * Coordinates are purely widget-local; it reads mouse position from [context].
 */
internal class SliceSliderWidget(
    private val value: IntValue,
    private val maxProvider: () -> Int,
    private val handleNormal: IDrawable,
    private val handleHover: IDrawable,
    private val handlePressed: IDrawable,
    private val handleW: Int = 7,
    private val handleH: Int = 38,
    private val trackPaddingX: Int = 2,
    private val trackPaddingY: Int = 10,
    private val onChanged: ((Int) -> Unit)? = null
) : Widget<SliceSliderWidget>(), Interactable {

    private var dragging = false

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val hovering = isHovering

        val max = maxProvider().coerceAtLeast(0)
        val v = value.getIntValue().coerceIn(0, max)
        if (v != value.getIntValue()) value.setIntValue(v)

        val trackX = trackPaddingX
        val trackY = trackPaddingY
        val trackW = (area.w() - trackPaddingX * 2).coerceAtLeast(1)
        val trackH = (area.h() - trackPaddingY * 2).coerceAtLeast(1)

        val usableH = (trackH - handleH).coerceAtLeast(1)
        val frac = if (max == 0) 0.0 else v.toDouble() / max.toDouble()
        val handleY = trackY + (usableH * frac).roundToInt()
        val handleX = trackX + (trackW - handleW) / 2

        val handleDrawable = when {
            dragging -> handlePressed
            hovering -> handleHover
            else -> handleNormal
        }

        // draw handle (widget-local coordinates; viewport stack is already translated)
        handleDrawable.draw(context, handleX, handleY, handleW, handleH, getActiveWidgetTheme(widgetTheme, hovering))

        super.draw(context, widgetTheme)
    }

    override fun onMousePressed(mouseButton: Int): Result {
        if (mouseButton != 0) return Result.IGNORE
        if (!isHovering) return Result.IGNORE
        dragging = true
        updateFromMouse()
        Interactable.playButtonClickSound()
        return Result.SUCCESS
    }

    override fun onMouseDrag(mouseButton: Int, timeSinceClick: Long) {
        if (!dragging || mouseButton != 0) return
        updateFromMouse()
    }

    override fun onMouseRelease(mouseButton: Int): Boolean {
        if (mouseButton != 0) return false
        if (!dragging) return false
        dragging = false
        updateFromMouse()
        return true
    }

    private fun updateFromMouse() {
        val ctx = context
        val max = maxProvider().coerceAtLeast(0)
        if (max == 0) {
            if (value.getIntValue() != 0) {
                value.setIntValue(0)
                onChanged?.invoke(0)
            }
            return
        }

        // Interactable callbacks are invoked with this widget's matrix already applied
        // (see ModularUI's LocatedWidget.applyMatrix). Therefore getMouseY() is already
        // in widget-local coordinates and must NOT be mixed with area.ry (relative coord)
        // or absMouseY (screen coord).
        val localY = ctx.mouseY

        val trackY = trackPaddingY
        val trackH = (area.h() - trackPaddingY * 2).coerceAtLeast(1)
        val usableH = (trackH - handleH).coerceAtLeast(1)

        val clamped = (localY - trackY - handleH / 2).coerceIn(0, usableH)
        val frac = clamped.toDouble() / usableH.toDouble()
        val next = (frac * max.toDouble()).roundToInt().coerceIn(0, max)

        if (next != value.getIntValue()) {
            value.setIntValue(next)
            onChanged?.invoke(next)
        }
    }
}
