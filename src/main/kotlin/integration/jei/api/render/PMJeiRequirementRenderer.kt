package github.kasuminova.prototypemachinery.integration.jei.api.render

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector

/**
 * Renderer for a [RecipeRequirementType] in JEI.
 *
 * Design goals:
 * - A single requirement component may map to multiple nodes.
 * - Each node may have multiple visual variants.
 * - Layout decides how many instances and which variants to place.
 */
public interface PMJeiRequirementRenderer<C : RecipeRequirementComponent> {

    public val type: RecipeRequirementType<C>

    /** Split a component into renderable nodes. */
    public fun split(ctx: JeiRecipeContext, component: C): List<PMJeiRequirementNode<C>>

    /**
     * Variants available for a node.
     *
     * Renderer is responsible for providing built-in variants (e.g. different tank sizes).
     */
    public fun variants(ctx: JeiRecipeContext, node: PMJeiRequirementNode<C>): List<PMJeiRendererVariant>

    /** Pick a default variant for a node. Layout may override this choice. */
    public fun defaultVariant(ctx: JeiRecipeContext, node: PMJeiRequirementNode<C>): PMJeiRendererVariant

    /** Declare JEI ingredient slot(s) for a node+variant at the given position. */
    public fun declareJeiSlots(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<C>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: JeiSlotCollector,
    )

    /**
     * Build ModularUI widgets for non-ingredient visuals (slot frame, labels, icons...).
     *
     * NOTE: We keep ModularUI types out of this interface for now; the actual builder bridge will be
     * introduced when implementing the ModularUI-in-JEI runtime.
     */
    public fun buildWidgets(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<C>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: PMJeiWidgetCollector,
    )
}
