package github.kasuminova.prototypemachinery.integration.jei.builtin.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
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
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

/**
 * Built-in JEI renderer for [ItemRequirementComponent].
 *
 * NOTE: For now this renderer only declares JEI item slots.
 * Visual slot frames/text overlays can be added later via ModularUI widgets.
 */
public object ItemRequirementJeiRenderer : PMJeiRequirementRenderer<ItemRequirementComponent> {
    /** Standard 18x18 item slot variant. */
    public val VARIANT_SLOT_18: ResourceLocation = ResourceLocation("prototypemachinery", "slot/18")

    private object Slot18Variant : PMJeiRendererVariant {
        override val id: ResourceLocation = VARIANT_SLOT_18
        override val width: Int = 18
        override val height: Int = 18
    }

    override val type: RecipeRequirementType<ItemRequirementComponent>
        get() = RecipeRequirementTypes.ITEM

    override fun split(ctx: JeiRecipeContext, component: ItemRequirementComponent): List<PMJeiRequirementNode<ItemRequirementComponent>> {
        val out = ArrayList<PMJeiRequirementNode<ItemRequirementComponent>>()

        // Inputs
        component.inputs.forEachIndexed { i, _ ->
            out += PMJeiRequirementNode(
                nodeId = "${component.id}:input:$i",
                type = type,
                component = component,
                role = PMJeiRequirementRole.INPUT,
                index = i,
            )
        }

        // Outputs
        component.outputs.forEachIndexed { i, _ ->
            out += PMJeiRequirementNode(
                nodeId = "${component.id}:output:$i",
                type = type,
                component = component,
                role = PMJeiRequirementRole.OUTPUT,
                index = i,
            )
        }

        return out
    }

    override fun variants(ctx: JeiRecipeContext, node: PMJeiRequirementNode<ItemRequirementComponent>): List<PMJeiRendererVariant> {
        return listOf(Slot18Variant)
    }

    override fun defaultVariant(ctx: JeiRecipeContext, node: PMJeiRequirementNode<ItemRequirementComponent>): PMJeiRendererVariant {
        return Slot18Variant
    }

    override fun declareJeiSlots(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<ItemRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: JeiSlotCollector,
    ) {
        val role = when (node.role) {
            PMJeiRequirementRole.INPUT, PMJeiRequirementRole.INPUT_PER_TICK -> JeiSlotRole.INPUT
            PMJeiRequirementRole.OUTPUT, PMJeiRequirementRole.OUTPUT_PER_TICK -> JeiSlotRole.OUTPUT
            else -> JeiSlotRole.INPUT
        }

        val index = out.nextIndex(JeiSlotKinds.ITEM)

        out.add(
            JeiSlot(
                kind = JeiSlotKinds.ITEM,
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
        node: PMJeiRequirementNode<ItemRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: PMJeiWidgetCollector,
    ) {
        // Intentionally empty for now.
        // JEI will render the item stack itself; we can add slot frame/labels later.
    }

    /** Resolve displayed stacks for a node (alternatives list). */
    public fun getDisplayedStacks(node: PMJeiRequirementNode<ItemRequirementComponent>): List<ItemStack> {
        val list = when (node.role) {
            PMJeiRequirementRole.INPUT, PMJeiRequirementRole.INPUT_PER_TICK -> node.component.inputs
            PMJeiRequirementRole.OUTPUT, PMJeiRequirementRole.OUTPUT_PER_TICK -> node.component.outputs
            else -> node.component.inputs
        }

        val key = list.getOrNull(node.index) ?: return emptyList()
        val stack = key.get()
        if (stack.isEmpty) return emptyList()

        // JEI expects Int stack size; cap to a reasonable display value.
        val max = stack.maxStackSize.coerceAtLeast(1)
        val desired = key.count.coerceAtLeast(1L).coerceAtMost(max.toLong()).toInt()

        val display = stack.copy()
        display.count = desired
        return listOf(display)
    }
}
