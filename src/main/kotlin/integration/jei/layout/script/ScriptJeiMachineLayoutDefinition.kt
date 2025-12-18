package github.kasuminova.prototypemachinery.integration.jei.layout.script

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutBuilder
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutRequirementsView
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiMachineLayoutDefinition
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiRequirementRendererRegistry

/**
 * Layout definition that executes a data-driven [ScriptJeiLayoutSpec].
 */
public class ScriptJeiMachineLayoutDefinition(
    private val spec: ScriptJeiLayoutSpec,
) : PMJeiMachineLayoutDefinition {

    override val width: Int = spec.width

    override val height: Int = spec.height

    override fun build(
        ctx: JeiRecipeContext,
        requirements: PMJeiLayoutRequirementsView,
        out: PMJeiLayoutBuilder,
    ) {
        val placed: MutableSet<String> = HashSet()

        val input = ScriptJeiLayoutInput(
            requirementNodes = requirements.all.map { n ->
                ScriptJeiLayoutNodeView(
                    nodeId = n.nodeId,
                    typeId = n.type.id,
                    role = n.role,
                    index = n.index,
                )
            }
        )

        val output = ScriptJeiLayoutOutput(
            placedNodeIds = placed,
            placeNode = { nodeId, x, y, variantId -> out.placeNode(nodeId, x, y, variantId) },
            placeFixedSlot = { providerId, role, x, y, width, height -> out.placeFixedSlot(providerId, role, x, y, width, height) },
            addDecorator = { decoratorId, x, y, data -> out.addDecorator(decoratorId, x, y, data) },
        )

        for (rule in spec.rules) {
            if (!rule.condition.test(input)) continue
            try {
                rule.apply(input, output)
            } catch (t: Throwable) {
                PrototypeMachinery.logger.error(
                    "JEI: script layout rule '${rule::class.java.name}' failed for recipe='${ctx.recipeId}'.",
                    t
                )
            }
        }

        spec.autoPlaceRemaining?.let { auto ->
            autoPlaceRemaining(ctx, requirements, out, placed, auto, width = width, height = height)
        }
    }
}

private fun autoPlaceRemaining(
    ctx: JeiRecipeContext,
    requirements: PMJeiLayoutRequirementsView,
    out: PMJeiLayoutBuilder,
    placed: Set<String>,
    spec: AutoPlaceRemainingSpec,
    width: Int,
    height: Int,
) {
    val remaining = requirements.all.filter { it.nodeId !in placed }
    if (remaining.isEmpty()) return

    var x = spec.startX.coerceAtLeast(spec.pad)
    var y = spec.startY.coerceAtLeast(spec.pad)

    val maxRight = (width - spec.pad).coerceAtLeast(spec.pad)
    val maxBottom = (height - spec.pad).coerceAtLeast(spec.pad)

    var rowH = 0

    for (n in remaining) {
        val renderer = getUnsafeRenderer(n.type) ?: continue

        @Suppress("UNCHECKED_CAST")
        val castNode = n as PMJeiRequirementNode<RecipeRequirementComponent>

        val variant = renderer.defaultVariant(ctx, castNode)
        val w = variant.width.coerceAtLeast(1)
        val h = variant.height.coerceAtLeast(1)

        if (x + w > maxRight) {
            x = spec.startX.coerceAtLeast(spec.pad)
            y += rowH + spec.gapY
            rowH = 0
        }

        if (y + h > maxBottom) {
            break
        }

        out.placeNode(n.nodeId, x, y, variant.id)
        x += w + spec.gapX
        rowH = maxOf(rowH, h)
    }
}

private fun getUnsafeRenderer(
    type: RecipeRequirementType<*>,
): PMJeiRequirementRenderer<RecipeRequirementComponent>? {
    @Suppress("UNCHECKED_CAST")
    return JeiRequirementRendererRegistry.get(type as RecipeRequirementType<RecipeRequirementComponent>)
}
