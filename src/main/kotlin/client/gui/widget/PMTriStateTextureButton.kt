package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.api.widget.Interactable.Result
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget

/**
 * A small texture button with visual states.
 *
 * States:
 * - normal: default
 * - hover: when hovering
 * - pressed: while mouse button is held down over the widget
 * - disabled: when [isEnabled] is false
 */
public class PMTriStateTextureButton(
    private val normal: IDrawable,
    private val hover: IDrawable,
    private val pressed: IDrawable,
    private val disabled: IDrawable? = null,
    private val onClick: (() -> Unit)? = null
) : Widget<PMTriStateTextureButton>(), Interactable {

    private var mouseDown: Boolean = false

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val hovering = isHovering
        val drawable = when {
            !isEnabled -> (disabled ?: normal)
            mouseDown && hovering -> pressed
            hovering -> hover
            else -> normal
        }
        drawable.drawAtZero(context, area, getActiveWidgetTheme(widgetTheme, hovering))
        super.draw(context, widgetTheme)
    }

    override fun onMousePressed(mouseButton: Int): Result {
        if (mouseButton != 0) return Result.IGNORE
        if (!isEnabled) return Result.IGNORE
        if (!isHovering) return Result.IGNORE
        mouseDown = true
        Interactable.playButtonClickSound()
        return Result.SUCCESS
    }

    override fun onMouseRelease(mouseButton: Int): Boolean {
        if (mouseButton != 0) return false
        if (!mouseDown) return false
        mouseDown = false
        // Return true so a tap can be processed.
        return true
    }

    override fun onMouseTapped(mouseButton: Int): Result {
        if (mouseButton != 0) return Result.IGNORE
        if (!isEnabled) return Result.IGNORE
        if (!isHovering) return Result.IGNORE
        onClick?.invoke()
        return Result.SUCCESS
    }
}
