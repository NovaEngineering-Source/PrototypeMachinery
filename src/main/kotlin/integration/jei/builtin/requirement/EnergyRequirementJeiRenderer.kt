package github.kasuminova.prototypemachinery.integration.jei.builtin.requirement

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.widgets.TextWidget
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotCollector
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRendererVariant
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import net.minecraft.util.ResourceLocation

/**
 * Built-in JEI renderer for [EnergyRequirementComponent].
 *
 * Energy is not a JEI ingredient type, so we render it via ModularUI widgets only.
 */
public object EnergyRequirementJeiRenderer : PMJeiRequirementRenderer<EnergyRequirementComponent> {

    private data class TextVariant(
        override val id: ResourceLocation,
        override val width: Int,
        override val height: Int,
    ) : PMJeiRendererVariant

    private val textVariant: PMJeiRendererVariant = TextVariant(
        id = ResourceLocation("prototypemachinery", "text/energy"),
        width = 80,
        height = 10,
    )

    override val type: RecipeRequirementType<EnergyRequirementComponent> = RecipeRequirementTypes.ENERGY

    override fun split(ctx: JeiRecipeContext, component: EnergyRequirementComponent): List<PMJeiRequirementNode<EnergyRequirementComponent>> {
        val out = ArrayList<PMJeiRequirementNode<EnergyRequirementComponent>>()

        fun addIf(value: Long, role: PMJeiRequirementRole, roleId: String) {
            if (value == 0L) return
            out += PMJeiRequirementNode(
                nodeId = "${component.id}:$roleId:0",
                type = type,
                component = component,
                role = role,
                index = 0,
            )
        }

        addIf(component.input, PMJeiRequirementRole.INPUT, "input")
        addIf(component.output, PMJeiRequirementRole.OUTPUT, "output")
        addIf(component.inputPerTick, PMJeiRequirementRole.INPUT_PER_TICK, "input_per_tick")
        addIf(component.outputPerTick, PMJeiRequirementRole.OUTPUT_PER_TICK, "output_per_tick")

        return out
    }

    override fun variants(ctx: JeiRecipeContext, node: PMJeiRequirementNode<EnergyRequirementComponent>): List<PMJeiRendererVariant> {
        return listOf(textVariant)
    }

    override fun defaultVariant(ctx: JeiRecipeContext, node: PMJeiRequirementNode<EnergyRequirementComponent>): PMJeiRendererVariant {
        return textVariant
    }

    override fun declareJeiSlots(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<EnergyRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: JeiSlotCollector,
    ) {
        // No JEI ingredient slots for energy.
    }

    override fun buildWidgets(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<EnergyRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: PMJeiWidgetCollector,
    ) {
        val text = formatEnergyText(node)
        out.add(
            TextWidget(IKey.str(text))
                .pos(x, y)
        )
    }

    private fun formatEnergyText(node: PMJeiRequirementNode<EnergyRequirementComponent>): String {
        val c = node.component
        val suffix = when (node.role) {
            PMJeiRequirementRole.INPUT -> "FE"
            PMJeiRequirementRole.OUTPUT -> "FE"
            PMJeiRequirementRole.INPUT_PER_TICK -> "FE/t"
            PMJeiRequirementRole.OUTPUT_PER_TICK -> "FE/t"
            else -> "FE"
        }

        val value = when (node.role) {
            PMJeiRequirementRole.INPUT -> -c.input
            PMJeiRequirementRole.OUTPUT -> c.output
            PMJeiRequirementRole.INPUT_PER_TICK -> -c.inputPerTick
            PMJeiRequirementRole.OUTPUT_PER_TICK -> c.outputPerTick
            else -> 0L
        }

        return if (value >= 0) {
            "+$value $suffix"
        } else {
            "$value $suffix"
        }
    }
}
