package github.kasuminova.prototypemachinery.integration.jei.api.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode

/**
 * Provides JEI-indexable ingredient values for a requirement node.
 *
 * Renderers focus on slot declaration + ModularUI widgets; this provider focuses on
 * "what should JEI index / rotate for this node".
 */
public interface PMJeiNodeIngredientProvider<C : RecipeRequirementComponent, T : Any> {

    public val type: RecipeRequirementType<C>

    /** Which [JeiSlotKind] this provider emits. Should match the renderer's declared slot kind. */
    public val kind: JeiSlotKind

    /**
     * Return displayed alternatives for this node.
     *
     * For classic items/fluids this is typically a single-element list.
     * For ore-dict/tag-like inputs, this can be multiple alternatives.
     */
    public fun getDisplayed(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<C>,
    ): List<T>
}
