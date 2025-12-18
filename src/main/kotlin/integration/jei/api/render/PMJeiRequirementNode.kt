package github.kasuminova.prototypemachinery.integration.jei.api.render

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole

/**
 * A renderable unit derived from a requirement component.
 *
 * One requirement component may produce multiple nodes (e.g. multiple item/fluid entries).
 * Layouts may place nodes multiple times and may choose different variants per node.
 */
public data class PMJeiRequirementNode<C : RecipeRequirementComponent>(
    /**
     * Stable node id within the recipe screen.
     *
     * Recommended format: "<componentId>:<role>:<index>".
     */
    public val nodeId: String,

    public val type: RecipeRequirementType<C>,

    public val component: C,

    /** UI role for layout purposes (input/output/per-tick/etc.). */
    public val role: PMJeiRequirementRole = PMJeiRequirementRole.OTHER,

    /** Index inside the originating component (if any). */
    public val index: Int = 0,
)
