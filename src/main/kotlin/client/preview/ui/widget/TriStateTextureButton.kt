package github.kasuminova.prototypemachinery.client.preview.ui.widget

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.api.widget.Interactable.Result
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.value.BoolValue
import com.cleanroommc.modularui.widget.Widget

/**
 * A small texture-only button with 3 visual states.
 *
 * Supported modes:
 * - Momentary button: normal / hover / pressed
 * - Toggle button: normal / hover / pressed, plus optional enabled state texture
 */
internal class TriStateTextureButton(
    private val normal: IDrawable,
    private val hover: IDrawable,
    private val pressed: IDrawable,
    private val enabled: IDrawable? = null,
    private val toggle: BoolValue? = null,
    private val onClick: (() -> Unit)? = null
) : Widget<TriStateTextureButton>(), Interactable {

    private var mouseDown: Boolean = false

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val enabled = toggle?.getBoolValue() == true
        val hovering = isHovering
        val drawable = when {
            mouseDown && hovering -> this.pressed
            enabled -> (this.enabled ?: hover)
            hovering -> hover
            else -> normal
        }
        drawable.drawAtZero(context, area, getActiveWidgetTheme(widgetTheme, hovering))
        super.draw(context, widgetTheme)
    }

    override fun onMousePressed(mouseButton: Int): Result {
        if (mouseButton != 0) return Result.IGNORE
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
        if (!isHovering) return Result.IGNORE

        if (toggle != null) {
            toggle.setBoolValue(!toggle.getBoolValue())
        }
        onClick?.invoke()
        return Result.SUCCESS
    }
}
