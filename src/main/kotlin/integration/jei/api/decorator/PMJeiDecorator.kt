package github.kasuminova.prototypemachinery.integration.jei.api.decorator

import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import net.minecraft.util.ResourceLocation

/**
 * JEI screen decorator.
 *
 * Decorators are not bound to a specific requirement type.
 * They provide cross-cutting visuals like backgrounds, progress bars, and dynamic texts
 * derived from recipe data.
 */
public interface PMJeiDecorator {

    /** Stable decorator id, used for registration and layout selection. */
    public val id: ResourceLocation

    /**
     * Build UI widgets for this decorator.
     *
     * NOTE: Widgets are collected through an indirection to keep this JEI-API agnostic.
     */
    public fun buildWidgets(
        ctx: JeiRecipeContext,
        x: Int,
        y: Int,
        data: Map<String, Any>,
        out: PMJeiWidgetCollector,
    )
}
