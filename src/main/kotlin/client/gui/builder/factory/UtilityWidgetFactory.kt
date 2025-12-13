package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.api.ui.definition.SpacerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TooltipAreaDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext

public class UtilityWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is SpacerDefinition -> buildSpacer(def)
            is TooltipAreaDefinition -> buildTooltipArea(def)
            else -> null
        }
    }

    private fun buildSpacer(def: SpacerDefinition): IWidget {
        return Widget()
            .pos(def.x, def.y)
            .size(def.width, def.height)
    }

    private fun buildTooltipArea(def: TooltipAreaDefinition): IWidget {
        val widget = Widget()
            .pos(def.x, def.y)
            .size(def.width, def.height)

        // TODO: Add tooltip using widget.tooltip()

        return widget
    }
}
