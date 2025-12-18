package github.kasuminova.prototypemachinery.integration.jei.layout

import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutBuilder
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutRequirementsView
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiMachineLayoutDefinition
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.ProgressArrowJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.RecipeDurationTextJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.ItemRequirementJeiRenderer

/**
 * A small machine-specific layout for the example machine:
 * `prototypemachinery:example_recipe_processor_hatches`
 */
public object ExampleRecipeProcessorHatchesJeiLayout : PMJeiMachineLayoutDefinition {

    override val width: Int = 160

    override val height: Int = 80

    override fun build(
        ctx: JeiRecipeContext,
        requirements: PMJeiLayoutRequirementsView,
        out: PMJeiLayoutBuilder,
    ) {
        // Left: up to 4 item inputs (2x2)
        val inputs = requirements.byRole(PMJeiRequirementRole.INPUT)
            .filter { it.component is ItemRequirementComponent }
            .map { it.nodeId }

        // Right: up to 4 item outputs (2x2)
        val outputs = requirements.byRole(PMJeiRequirementRole.OUTPUT)
            .filter { it.component is ItemRequirementComponent }
            .map { it.nodeId }

        val slot = ItemRequirementJeiRenderer.VARIANT_SLOT_18

        // 2x2 grid positions
        val leftX0 = 12
        val leftY0 = 18
        val dx = 20
        val dy = 20

        fun placeGrid(nodeIds: List<String>, x0: Int, y0: Int) {
            nodeIds.getOrNull(0)?.let { out.placeNode(it, x0, y0, slot) }
            nodeIds.getOrNull(1)?.let { out.placeNode(it, x0 + dx, y0, slot) }
            nodeIds.getOrNull(2)?.let { out.placeNode(it, x0, y0 + dy, slot) }
            nodeIds.getOrNull(3)?.let { out.placeNode(it, x0 + dx, y0 + dy, slot) }
        }

        placeGrid(inputs, leftX0, leftY0)
        placeGrid(outputs, width - leftX0 - dx - 18, leftY0)

        // Center: progress arrow + duration text (decorators demo).
        // Note: these are purely visual and do not affect JEI indexing.
        val centerArrowX = (width - 20) / 2
        val centerArrowY = (height - 20) / 2 - 4
        out.addDecorator(
            decoratorId = ProgressArrowJeiDecorator.id,
            x = centerArrowX,
            y = centerArrowY,
            data = mapOf(
                "style" to "arrow",
                "direction" to "RIGHT",
                // Make the animation speed loosely match the recipe duration.
                "cycleTicks" to ctx.recipe.durationTicks.coerceAtLeast(1),
            ),
        )

        out.addDecorator(
            decoratorId = RecipeDurationTextJeiDecorator.id,
            x = (width - 80) / 2,
            y = centerArrowY + 22,
            data = mapOf(
                "width" to 80,
                "height" to 10,
                "align" to "CENTER",
                "template" to "{ticks} t ({seconds}s)",
                "shadow" to true,
            ),
        )
    }
}
