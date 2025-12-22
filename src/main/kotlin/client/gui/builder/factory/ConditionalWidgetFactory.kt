package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.widget.ParentWidget
import github.kasuminova.prototypemachinery.api.ui.definition.ConditionalDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext

/**
 * Applies visibleIf/enabledIf conditions to an already built widget.
 *
 * We implement "visibility" by toggling [Widget.isEnabled], which matches existing usage
 * in StructurePreview UI and keeps the implementation lightweight.
 */
public class ConditionalWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        if (def !is ConditionalDefinition) return null

        val inner = buildChild(def.content) ?: return null
        val visibleKey = ctx.bindings.bindingKey(def.visibleIf)
        val enabledKey = ctx.bindings.bindingKey(def.enabledIf)

        // Fast path: no conditions
        if (visibleKey == null && enabledKey == null) return inner

        val visibleSync = visibleKey?.let { ctx.bindings.ensureBoolBindingExpr(ctx.syncManager, ctx.machineTile, it).syncValue }
        val enabledSync = enabledKey?.let { ctx.bindings.ensureBoolBindingExpr(ctx.syncManager, ctx.machineTile, it).syncValue }

        // Wrap the child so we can reliably attach an update listener (Widget<W> is self-typed in Java).
        // Enabling/disabling the wrapper effectively toggles the whole subtree.
        val wrapper = ParentWidget()
        wrapper.child(inner)
        wrapper.onUpdateListener {
            val v = visibleSync?.boolValue ?: true
            val e = enabledSync?.boolValue ?: true
            val next = v && e
            if (wrapper.isEnabled != next) wrapper.isEnabled = next
        }

        return wrapper
    }
}
