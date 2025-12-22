package github.kasuminova.prototypemachinery.client.gui.builder

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.screen.viewport.GuiContext
import com.cleanroommc.modularui.theme.WidgetTheme

/**
 * A drawable that delegates drawing to a supplier at render time.
 *
 * Useful for widgets that need state-dependent textures (hover/pressed) but only accept a single IDrawable.
 */
public class DynamicDrawable(
    private val supplier: () -> IDrawable
) : IDrawable {

    override fun draw(context: GuiContext, x: Int, y: Int, width: Int, height: Int, widgetTheme: WidgetTheme) {
        supplier().draw(context, x, y, width, height, widgetTheme)
    }

    override fun canApplyTheme(): Boolean {
        // Delegate the info; default to false if supplier throws.
        return try {
            supplier().canApplyTheme()
        } catch (_: Throwable) {
            false
        }
    }
}
