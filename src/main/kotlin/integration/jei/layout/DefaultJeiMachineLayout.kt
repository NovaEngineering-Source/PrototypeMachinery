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
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiIcons
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.ProgressModuleJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.builtin.decorator.RecipeDurationTextJeiDecorator
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiRequirementRendererRegistry
import net.minecraft.util.ResourceLocation

/**
 * Default auto layout using the new plaid and module assets.
 */
public object DefaultJeiMachineLayout : PMJeiMachineLayoutDefinition {

    override val width: Int = 170

    override val height: Int = 82

    override fun build(
        ctx: JeiRecipeContext,
        requirements: PMJeiLayoutRequirementsView,
        out: PMJeiLayoutBuilder,
    ) {
        val pad = 4

        // Track which nodes are explicitly placed by this default layout.
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
                trackingOut.placeNode(nodes[idx++], x0, y, PMJeiIcons.ITEM_PLAID)
                if (idx >= nodes.size) break
                trackingOut.placeNode(nodes[idx++], x1, y, PMJeiIcons.ITEM_PLAID)
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

        val maxItemRows = ((height - pad * 2 + itemGap) / (itemH + itemGap)).coerceAtLeast(1)

        placeItemGrid(itemInputs, leftItemX0, leftItemX1, pad, maxItemRows)
        placeItemGrid(itemOutputs, rightItemX0, rightItemX1, pad, maxItemRows)

        // ------------------------------------------
        // Fluids: one column per side
        // ------------------------------------------
        val tankMain = PMJeiIcons.FLUID_1X3 // 18x54
        val tankSmall = PMJeiIcons.FLUID_1X1 // 18x18
        val tankW = 18
        val tankGap = 4

        val leftTankX = leftItemX1 + itemW + tankGap
        val rightTankX = rightItemX0 - tankGap - tankW

        val fluidInputs = requirements.byRole(PMJeiRequirementRole.INPUT)
            .filter { it.component is FluidRequirementComponent }
            .map { it.nodeId }
        val fluidOutputs = requirements.byRole(PMJeiRequirementRole.OUTPUT)
            .filter { it.component is FluidRequirementComponent }
            .map { it.nodeId }

        fluidInputs.firstOrNull()?.let { trackingOut.placeNode(it, leftTankX, pad, tankMain) }
        fluidOutputs.firstOrNull()?.let { trackingOut.placeNode(it, rightTankX, pad, tankMain) }

        val smallTankY = (height - pad - 18).coerceAtLeast(pad)
        fluidInputs.getOrNull(1)?.let { trackingOut.placeNode(it, leftTankX, smallTankY, tankSmall) }
        fluidOutputs.getOrNull(1)?.let { trackingOut.placeNode(it, rightTankX, smallTankY, tankSmall) }

        // ------------------------------------------
        // Energy & Progress: Center Area
        // ------------------------------------------
        val midAreaX = leftTankX + tankW + tankGap
        val midAreaW = rightTankX - tankGap - midAreaX
        val midX = midAreaX + midAreaW / 2

        // Energy Bar (18x54)
        val energyNodes = requirements.all.filter { it.component is EnergyRequirementComponent }
        if (energyNodes.isNotEmpty()) {
            trackingOut.placeNode(energyNodes[0].nodeId, midAreaX, pad, PMJeiIcons.ENERGY_1X3)
        }

        // Progress Module (24x17)
        val progressX = midX - 12
        val progressY = (height - 17) / 2
        trackingOut.addDecorator(
            decoratorId = ProgressModuleJeiDecorator.id,
            x = progressX,
            y = progressY,
            data = mapOf(
                "type" to "right",
                "direction" to "RIGHT",
                "cycleTicks" to ctx.recipe.durationTicks.coerceAtLeast(1)
            )
        )

        // Duration Text
        trackingOut.addDecorator(
            decoratorId = RecipeDurationTextJeiDecorator.id,
            x = midX - 30,
            y = progressY + 20,
            data = mapOf(
                "width" to 60,
                "height" to 10,
                "align" to "CENTER",
                "template" to "{ticks} t",
                "shadow" to true
            )
        )

        // Parallelism (if any)
        val parallelismNodes = requirements.byRole(PMJeiRequirementRole.OTHER)
            .filter { it.component is ParallelismRequirementComponent }
            .map { it.nodeId }
        if (parallelismNodes.isNotEmpty()) {
            trackingOut.placeNode(parallelismNodes[0], midX - 30, progressY - 15, null)
        }

        // ------------------------------------------
        // Fallback: auto-place any remaining nodes
        // ------------------------------------------
        val remaining = requirements.all.filter { it.nodeId !in placed }
        if (remaining.isNotEmpty()) {
            var x = midAreaX
            var y = pad + 60 // Below energy/progress if possible
            val maxX = rightTankX - tankGap
            var rowH = 0

            for (n in remaining) {
                val renderer = getUnsafeRenderer(n.type) ?: continue
                @Suppress("UNCHECKED_CAST")
                val castNode = n as github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode<github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent>
                val variant = renderer.defaultVariant(ctx, castNode)

                val w = variant.width.coerceAtLeast(1)
                val h = variant.height.coerceAtLeast(1)

                if (x + w > maxX) {
                    x = midAreaX
                    y += rowH + 2
                    rowH = 0
                }
                if (y + h > height - pad) break

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
