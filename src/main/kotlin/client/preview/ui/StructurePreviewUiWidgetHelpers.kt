package github.kasuminova.prototypemachinery.client.preview.ui

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.ListWidget

internal class ToggleDrawableWidget(
    private val valueProvider: () -> Boolean,
    private val off: IDrawable,
    private val on: IDrawable
) : com.cleanroommc.modularui.widget.Widget<ToggleDrawableWidget>() {
    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val drawable = if (valueProvider()) on else off
        drawable.drawAtZero(context, area, getActiveWidgetTheme(widgetTheme, isHovering))
        super.draw(context, widgetTheme)
    }
}

/** Button helper for Kotlin (avoid dealing with W extends ButtonWidget<W>). */
internal class UiButton : ButtonWidget<UiButton>()

internal class UiHotspot : com.cleanroommc.modularui.widget.Widget<UiHotspot>()

/**
 * Kotlin helper to avoid dealing with ModularUI's self-referential generics
 * (ListWidget<I, W extends ListWidget<I, W>>) at call sites.
 */
internal class WidgetList : ListWidget<IWidget, WidgetList>()
