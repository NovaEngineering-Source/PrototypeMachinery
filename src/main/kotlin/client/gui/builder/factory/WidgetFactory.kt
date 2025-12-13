package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.IWidget
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext

/**
 * Small pluggable factory that can build a ModularUI widget from a [WidgetDefinition].
 *
 * Return null when the factory does not support the given definition.
 */
public fun interface WidgetFactory {

    public fun build(
        def: WidgetDefinition,
        ctx: UIBuildContext,
        buildChild: (WidgetDefinition) -> IWidget?
    ): IWidget?
}
