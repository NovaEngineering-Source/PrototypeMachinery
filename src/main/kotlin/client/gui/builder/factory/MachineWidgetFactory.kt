package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.IWidget
import github.kasuminova.prototypemachinery.api.machine.component.getFirstComponentOfType
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.ui.definition.FactoryRecipeProgressListDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext
import github.kasuminova.prototypemachinery.client.gui.sync.FactoryRecipeProgressSyncHandler
import github.kasuminova.prototypemachinery.client.gui.widget.FactoryRecipeProgressListWidget

/**
 * Factory for machine-specific widgets that are exposed to ZenScript.
 */
public class MachineWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is FactoryRecipeProgressListDefinition -> buildFactoryRecipeProgressList(def, ctx)
            else -> null
        }
    }

    private fun buildFactoryRecipeProgressList(def: FactoryRecipeProgressListDefinition, ctx: UIBuildContext): IWidget? {
        val hasProcessor = ctx.machineTile.machine.componentMap.getFirstComponentOfType<FactoryRecipeProcessorComponent>() != null
        if (!hasProcessor) return null

        val sync = FactoryRecipeProgressSyncHandler(ctx.machineTile)
        ctx.syncManager.syncValue(def.syncKey, sync)

        return FactoryRecipeProgressListWidget(sync, def)
            .pos(def.x, def.y)
            .size(def.width, def.height)
    }
}
