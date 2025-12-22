package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.ITooltip
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.api.ui.definition.SpacerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TooltipAreaDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TooltipWrapperDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext

public class UtilityWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is SpacerDefinition -> buildSpacer(def)
            is TooltipAreaDefinition -> buildTooltipArea(def)
            is TooltipWrapperDefinition -> buildTooltipWrapper(def, ctx, buildChild)
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

        if (def.tooltipLines.isNotEmpty()) {
            widget.addTooltipStringLines(def.tooltipLines)
        }

        return widget
    }

    private fun buildTooltipWrapper(def: TooltipWrapperDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        val child = buildChild(def.content) ?: return null

        val tip = child as? ITooltip<*> ?: return child

        val staticLines = def.tooltipLines
        val rawKey = ctx.bindings.bindingKey(def.tooltipKey)
        val dynamicSync = rawKey?.let { k ->
            ctx.bindings.ensureStringBinding(ctx.syncManager, ctx.machineTile, k).syncValue
        }

        if (staticLines.isNotEmpty() || dynamicSync != null) {
            // Use dynamic builder so static + dynamic content stays consistent and doesn't stack.
            if (dynamicSync != null) {
                tip.tooltipAutoUpdate(true)
            }
            tip.tooltipDynamic { tooltip ->
                if (staticLines.isNotEmpty()) {
                    tooltip.addStringLines(staticLines)
                }
                val dyn = dynamicSync?.stringValue
                if (!dyn.isNullOrBlank()) {
                    tooltip.addStringLines(dyn.split('\n'))
                }
            }
        }

        return child
    }
}
