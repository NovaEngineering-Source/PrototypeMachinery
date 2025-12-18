package github.kasuminova.prototypemachinery.integration.jei.builtin.requirement

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.widgets.TextWidget
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotCollector
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRendererVariant
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import net.minecraft.util.ResourceLocation

/**
 * Built-in JEI renderer for [ParallelismRequirementComponent].
 *
 * This requirement adjusts the process attribute PROCESS_PARALLELISM.
 * It's not an ingredient, so we render it as text.
 */
public object ParallelismRequirementJeiRenderer : PMJeiRequirementRenderer<ParallelismRequirementComponent> {

    private data class TextVariant(
        override val id: ResourceLocation,
        override val width: Int,
        override val height: Int,
    ) : PMJeiRendererVariant

    private val textVariant: PMJeiRendererVariant = TextVariant(
        id = ResourceLocation("prototypemachinery", "text/parallelism"),
        width = 90,
        height = 10,
    )

    override val type: RecipeRequirementType<ParallelismRequirementComponent> = RecipeRequirementTypes.PARALLELISM

    override fun split(
        ctx: JeiRecipeContext,
        component: ParallelismRequirementComponent,
    ): List<PMJeiRequirementNode<ParallelismRequirementComponent>> {
        val p = component.parallelism.coerceAtLeast(1L)
        if (p <= 1L) return emptyList()

        return listOf(
            PMJeiRequirementNode(
                nodeId = "${component.id}:parallelism:0",
                type = type,
                component = component,
                role = PMJeiRequirementRole.OTHER,
                index = 0,
            )
        )
    }

    override fun variants(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<ParallelismRequirementComponent>,
    ): List<PMJeiRendererVariant> {
        return listOf(textVariant)
    }

    override fun defaultVariant(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<ParallelismRequirementComponent>,
    ): PMJeiRendererVariant {
        return textVariant
    }

    override fun declareJeiSlots(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<ParallelismRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: JeiSlotCollector,
    ) {
        // No JEI ingredient slots.
    }

    override fun buildWidgets(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<ParallelismRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: PMJeiWidgetCollector,
    ) {
        val p = node.component.parallelism.coerceAtLeast(1L)
        out.add(
            TextWidget(IKey.str("Parallelism x$p"))
                .pos(x, y)
        )
    }
}
