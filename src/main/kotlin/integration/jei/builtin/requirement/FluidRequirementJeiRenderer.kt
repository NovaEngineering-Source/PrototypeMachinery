package github.kasuminova.prototypemachinery.integration.jei.builtin.requirement

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotCollector
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRendererVariant
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack

/**
 * Built-in JEI renderer for [FluidRequirementComponent].
 *
 * Notes:
 * - JEI uses vanilla [FluidStack] with Int amount, but PM uses Long counts in [PMKey].
 *   We clamp to Int.MAX_VALUE for display.
 * - This renderer only declares JEI fluid slots. Visual frames/labels can be added via widgets later.
 */
public object FluidRequirementJeiRenderer : PMJeiRequirementRenderer<FluidRequirementComponent> {

    private data class TankVariant(
        override val id: ResourceLocation,
        override val width: Int,
        override val height: Int,
    ) : PMJeiRendererVariant

    public val VARIANT_TANK_16x58: ResourceLocation = ResourceLocation("prototypemachinery", "tank/16x58")
    public val VARIANT_TANK_16x34: ResourceLocation = ResourceLocation("prototypemachinery", "tank/16x34")
    public val VARIANT_TANK_24x58: ResourceLocation = ResourceLocation("prototypemachinery", "tank/24x58")

    private val variants: List<PMJeiRendererVariant> = listOf(
        TankVariant(VARIANT_TANK_16x58, 16, 58),
        TankVariant(VARIANT_TANK_16x34, 16, 34),
        TankVariant(VARIANT_TANK_24x58, 24, 58),
    )

    override val type: RecipeRequirementType<FluidRequirementComponent> = RecipeRequirementTypes.FLUID

    override fun split(ctx: JeiRecipeContext, component: FluidRequirementComponent): List<PMJeiRequirementNode<FluidRequirementComponent>> {
        val out = ArrayList<PMJeiRequirementNode<FluidRequirementComponent>>()

        fun addAll(list: List<PMKey<FluidStack>>, role: PMJeiRequirementRole, roleId: String) {
            for (i in list.indices) {
                out += PMJeiRequirementNode(
                    nodeId = "${component.id}:$roleId:$i",
                    type = type,
                    component = component,
                    role = role,
                    index = i,
                )
            }
        }

        addAll(component.inputs, PMJeiRequirementRole.INPUT, "input")
        addAll(component.outputs, PMJeiRequirementRole.OUTPUT, "output")
        addAll(component.inputsPerTick, PMJeiRequirementRole.INPUT_PER_TICK, "input_per_tick")
        addAll(component.outputsPerTick, PMJeiRequirementRole.OUTPUT_PER_TICK, "output_per_tick")

        return out
    }

    override fun variants(ctx: JeiRecipeContext, node: PMJeiRequirementNode<FluidRequirementComponent>): List<PMJeiRendererVariant> {
        return variants
    }

    override fun defaultVariant(ctx: JeiRecipeContext, node: PMJeiRequirementNode<FluidRequirementComponent>): PMJeiRendererVariant {
        return variants[0]
    }

    override fun declareJeiSlots(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<FluidRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: JeiSlotCollector,
    ) {
        val role = when (node.role) {
            PMJeiRequirementRole.INPUT, PMJeiRequirementRole.INPUT_PER_TICK -> JeiSlotRole.INPUT
            PMJeiRequirementRole.OUTPUT, PMJeiRequirementRole.OUTPUT_PER_TICK -> JeiSlotRole.OUTPUT
            else -> JeiSlotRole.CATALYST
        }

        val index = out.nextIndex(JeiSlotKinds.FLUID)
        out.add(
            JeiSlot(
                kind = JeiSlotKinds.FLUID,
                nodeId = node.nodeId,
                index = index,
                role = role,
                x = x,
                y = y,
                width = variant.width,
                height = variant.height,
            )
        )
    }

    override fun buildWidgets(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<FluidRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: PMJeiWidgetCollector,
    ) {
        // Intentionally empty for now (JEI draws tanks itself).
    }

    /**
     * Returns the displayed [FluidStack] list for a node (used to populate JEI fluid groups).
     */
    public fun getDisplayedFluids(node: PMJeiRequirementNode<FluidRequirementComponent>): List<FluidStack> {
        val key = getKey(node) ?: return emptyList()
        val stack = key.get()
        if (stack.amount <= 0) {
            // JEI generally expects a positive amount.
            stack.amount = 1
        }
        return listOf(stack)
    }

    /**
     * Best-effort capacity hint for JEI tank rendering.
     *
     * JEI needs a capacity at init time; we use max(1000, requestedAmount) and clamp to Int.
     */
    public fun getCapacityMb(node: PMJeiRequirementNode<FluidRequirementComponent>): Int {
        val key = getKey(node) ?: return 1000
        val requested = key.count.coerceAtLeast(0L)
        val cap = maxOf(1000L, requested)
        return cap.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun getKey(node: PMJeiRequirementNode<FluidRequirementComponent>): PMKey<FluidStack>? {
        val c = node.component
        val i = node.index
        return when (node.role) {
            PMJeiRequirementRole.INPUT -> c.inputs.getOrNull(i)
            PMJeiRequirementRole.OUTPUT -> c.outputs.getOrNull(i)
            PMJeiRequirementRole.INPUT_PER_TICK -> c.inputsPerTick.getOrNull(i)
            PMJeiRequirementRole.OUTPUT_PER_TICK -> c.outputsPerTick.getOrNull(i)
            else -> null
        }
    }
}
