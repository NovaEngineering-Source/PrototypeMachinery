package github.kasuminova.prototypemachinery.integration.jei.layout

import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutBuilder
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutRequirementsView
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiMachineLayoutDefinition
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.ProgressArrowJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.RecipeDurationTextJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.FluidRequirementJeiRenderer
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.ItemRequirementJeiRenderer
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiRequirementRendererRegistry
import net.minecraft.util.ResourceLocation

/**
 * Default auto layout.
 *
 * Goals:
 * - Always show something useful without machine-specific layout.
 * - Put inputs on the left and outputs on the right.
 * - Prefer item slots and one/two fluid tanks per side.
 * - Render energy requirements as text in the middle.
 */
public object DefaultJeiMachineLayout : PMJeiMachineLayoutDefinition {

    override val width: Int = 160

    override val height: Int = 80

    override fun build(
        ctx: JeiRecipeContext,
        requirements: PMJeiLayoutRequirementsView,
        out: PMJeiLayoutBuilder,
    ) {
        val pad = 4

        // Track which nodes are explicitly placed by this default layout.
        // Any remaining nodes (addon requirement types) will be auto-placed later.
        val placed: MutableSet<String> = HashSet()
        val trackingOut = object : PMJeiLayoutBuilder {
            override fun placeNode(nodeId: String, x: Int, y: Int, variantId: ResourceLocation?) {
                placed += nodeId
                out.placeNode(nodeId, x, y, variantId)
            }

            override fun addDecorator(decoratorId: ResourceLocation, x: Int, y: Int, data: Map<String, Any>) {
                out.addDecorator(decoratorId, x, y, data)
            }
        }

        // ------------------------------------------
        // Items: 2 columns on each side
        // ------------------------------------------
        val itemW = 18
        val itemH = 18
        val itemGap = 2

        val leftItemX0 = pad
        val leftItemX1 = pad + itemW + itemGap

        val rightItemX1 = width - pad - itemW
        val rightItemX0 = rightItemX1 - (itemW + itemGap)

        fun placeItemGrid(nodes: List<String>, x0: Int, x1: Int, startY: Int, maxRows: Int): Int {
            var idx = 0
            var y = startY
            for (row in 0 until maxRows) {
                if (idx >= nodes.size) break
                trackingOut.placeNode(nodes[idx++], x0, y, ItemRequirementJeiRenderer.VARIANT_SLOT_18)
                if (idx >= nodes.size) break
                trackingOut.placeNode(nodes[idx++], x1, y, ItemRequirementJeiRenderer.VARIANT_SLOT_18)
                y += itemH + itemGap
            }
            return idx
        }

        val itemInputs = requirements.byRole(PMJeiRequirementRole.INPUT)
            .filter { it.component is ItemRequirementComponent }
            .map { it.nodeId }
        val itemOutputs = requirements.byRole(PMJeiRequirementRole.OUTPUT)
            .filter { it.component is ItemRequirementComponent }
            .map { it.nodeId }

        // Limit grid to available height.
        val maxItemRows = ((height - pad * 2 + itemGap) / (itemH + itemGap)).coerceAtLeast(1)

        placeItemGrid(itemInputs, leftItemX0, leftItemX1, pad, maxItemRows)
        placeItemGrid(itemOutputs, rightItemX0, rightItemX1, pad, maxItemRows)

        // ------------------------------------------
        // Fluids: one column per side, use tank variant
        // ------------------------------------------
        val tankMain: ResourceLocation = FluidRequirementJeiRenderer.VARIANT_TANK_16x58
        val tankSmall: ResourceLocation = FluidRequirementJeiRenderer.VARIANT_TANK_16x34

        val tankW = 16
        val tankGap = 4

        // Put fluid tanks next to item grids.
        val leftTankX = leftItemX1 + itemW + tankGap
        val rightTankX = rightItemX0 - tankGap - tankW

        val fluidInputs = requirements.byRole(PMJeiRequirementRole.INPUT)
            .filter { it.component is FluidRequirementComponent }
            .map { it.nodeId }
        val fluidOutputs = requirements.byRole(PMJeiRequirementRole.OUTPUT)
            .filter { it.component is FluidRequirementComponent }
            .map { it.nodeId }

        // Prefer one big tank per side.
        fluidInputs.firstOrNull()?.let { trackingOut.placeNode(it, leftTankX, pad, tankMain) }
        fluidOutputs.firstOrNull()?.let { trackingOut.placeNode(it, rightTankX, pad, tankMain) }

        // Per-tick fluids (if any): show as smaller tanks near bottom.
        val fluidInputsPerTick = requirements.byRole(PMJeiRequirementRole.INPUT_PER_TICK)
            .filter { it.component is FluidRequirementComponent }
            .map { it.nodeId }
        val fluidOutputsPerTick = requirements.byRole(PMJeiRequirementRole.OUTPUT_PER_TICK)
            .filter { it.component is FluidRequirementComponent }
            .map { it.nodeId }

        val smallTankY = (height - pad - 34).coerceAtLeast(pad)
        fluidInputsPerTick.firstOrNull()?.let { trackingOut.placeNode(it, leftTankX, smallTankY, tankSmall) }
        fluidOutputsPerTick.firstOrNull()?.let { trackingOut.placeNode(it, rightTankX, smallTankY, tankSmall) }

        // ------------------------------------------
        // Energy: show up to 4 lines in the middle.
        // ------------------------------------------
        val midX = (width / 2) - 40
        var energyY = pad

        fun placeEnergy(role: PMJeiRequirementRole) {
            val nodes = requirements.byRole(role)
                .filter { it.component is EnergyRequirementComponent }
                .map { it.nodeId }
            for (id in nodes) {
                trackingOut.placeNode(id, midX, energyY, null)
                energyY += 10
            }
        }

        placeEnergy(PMJeiRequirementRole.INPUT)
        placeEnergy(PMJeiRequirementRole.OUTPUT)
        placeEnergy(PMJeiRequirementRole.INPUT_PER_TICK)
        placeEnergy(PMJeiRequirementRole.OUTPUT_PER_TICK)

        // Parallelism (non-ingredient text)
        val parallelismNodes = requirements.byRole(PMJeiRequirementRole.OTHER)
            .filter { it.component is ParallelismRequirementComponent }
            .map { it.nodeId }
        for (id in parallelismNodes) {
            trackingOut.placeNode(id, midX, energyY, null)
            energyY += 10
        }

        // Optional: if the center area ended up unused (no energy/parallelism text), show a progress arrow + duration.
        val hasEnergyText = requirements.byRole(PMJeiRequirementRole.INPUT).any { it.component is EnergyRequirementComponent } ||
            requirements.byRole(PMJeiRequirementRole.OUTPUT).any { it.component is EnergyRequirementComponent } ||
            requirements.byRole(PMJeiRequirementRole.INPUT_PER_TICK).any { it.component is EnergyRequirementComponent } ||
            requirements.byRole(PMJeiRequirementRole.OUTPUT_PER_TICK).any { it.component is EnergyRequirementComponent }

        val hasParallelismText = parallelismNodes.isNotEmpty()

        if (!hasEnergyText && !hasParallelismText) {
            val centerArrowX = (width - 20) / 2
            val centerArrowY = (height - 20) / 2
            trackingOut.addDecorator(
                decoratorId = ProgressArrowJeiDecorator.id,
                x = centerArrowX,
                y = centerArrowY,
                data = mapOf(
                    "style" to "arrow",
                    "direction" to "RIGHT",
                    "cycleTicks" to ctx.recipe.durationTicks.coerceAtLeast(1),
                ),
            )
            trackingOut.addDecorator(
                decoratorId = RecipeDurationTextJeiDecorator.id,
                x = (width - 80) / 2,
                y = centerArrowY + 22,
                data = mapOf(
                    "width" to 80,
                    "height" to 10,
                    "align" to "CENTER",
                    "template" to "{ticks} t",
                    "shadow" to true,
                ),
            )
        }

        // ------------------------------------------
        // Fallback: auto-place any remaining nodes (addon requirement types)
        // ------------------------------------------
        val remaining = requirements.all.filter { it.nodeId !in placed }
        if (remaining.isNotEmpty()) {
            var x = midX
            var y = (energyY + 2).coerceAtMost(height - pad)
            if (y > height - pad - 10) {
                // If the text stack already consumed most space, start from top of the middle area.
                y = pad
            }

            val maxX = width - pad
            var rowH = 0

            for (n in remaining) {
                val renderer = getUnsafeRenderer(n.type) ?: continue

                @Suppress("UNCHECKED_CAST")
                val castNode = n as github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode<github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent>
                val variant = renderer.defaultVariant(ctx, castNode)

                val w = variant.width.coerceAtLeast(1)
                val h = variant.height.coerceAtLeast(1)

                if (x + w > maxX) {
                    x = midX
                    y += rowH + 2
                    rowH = 0
                }

                if (y + h > height - pad) {
                    // No more space; stop placing.
                    break
                }

                trackingOut.placeNode(n.nodeId, x, y, variant.id)
                x += w + 2
                rowH = maxOf(rowH, h)
            }
        }
    }
}

private fun getUnsafeRenderer(
    type: github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType<*>,
): PMJeiRequirementRenderer<github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent>? {
    @Suppress("UNCHECKED_CAST")
    return (JeiRequirementRendererRegistry.get(type as github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType<github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent>))
}
