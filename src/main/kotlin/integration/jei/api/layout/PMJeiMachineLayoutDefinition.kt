package github.kasuminova.prototypemachinery.integration.jei.api.layout

import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext

/**
 * Per-machine JEI layout definition.
 *
 * NOTE: This interface describes a layout *definition*. It should be treated as immutable and
 * should not capture world/TileEntity state. This makes hot-reload safer.
 */
public interface PMJeiMachineLayoutDefinition {

    /** JEI background width. */
    public val width: Int

    /** JEI background height. */
    public val height: Int

    /**
     * Build a layout plan for the given recipe.
     *
     * Layout receives resolved requirement nodes to support:
     * - multiple node instances per requirement component
     * - selecting different renderer variants per node
     */
    public fun build(
        ctx: JeiRecipeContext,
        requirements: PMJeiLayoutRequirementsView,
        out: PMJeiLayoutBuilder,
    )
}
