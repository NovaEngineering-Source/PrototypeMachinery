package github.kasuminova.prototypemachinery.integration.jei.api.layout

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode

/**
 * Read-only view of resolved requirement nodes for layout building.
 */
public interface PMJeiLayoutRequirementsView {

    public val all: List<PMJeiRequirementNode<out RecipeRequirementComponent>>

    public fun byType(type: RecipeRequirementType<*>): List<PMJeiRequirementNode<out RecipeRequirementComponent>>

    public fun byRole(role: PMJeiRequirementRole): List<PMJeiRequirementNode<out RecipeRequirementComponent>>
}
