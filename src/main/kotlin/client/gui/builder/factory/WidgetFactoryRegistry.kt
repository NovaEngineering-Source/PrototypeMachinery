package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.IWidget
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext

public class WidgetFactoryRegistry(
    private val factories: List<WidgetFactory>
) {

    public fun buildWidget(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        for (factory in factories) {
            val widget = factory.build(def, ctx, buildChild)
            if (widget != null) return widget
        }
        return null
    }
}
