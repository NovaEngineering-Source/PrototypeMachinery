package github.kasuminova.prototypemachinery.integration.jei.builtin.requirement

import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.DynamicItemInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
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
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiIcons
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiRenderOptions
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

    private val variants: List<PMJeiRendererVariant> = listOf(
        Slot18Variant,
        PMJeiIcons.ALL_VARIANTS[PMJeiIcons.ITEM_PLAID]!!
    ) + PMJeiIcons.ALL_VARIANTS.values.filter { it.id.path.startsWith("plaid/base_") }

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

        // Fuzzy inputs
        @Suppress("UNCHECKED_CAST")
        val fuzzy = component.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<ItemStack>>
        if (!fuzzy.isNullOrEmpty()) {
            when (JeiRenderOptions.current().candidateSlotRenderMode) {
                JeiRenderOptions.CandidateSlotRenderMode.EXPANDED -> {
                    // Split into N slots: one per candidate.
                    fuzzy.forEachIndexed { groupIndex, group ->
                        for (candidateIndex in group.candidates.indices) {
                            out += PMJeiRequirementNode(
                                nodeId = "${component.id}:fuzzy_input:$groupIndex:$candidateIndex",
                                type = type,
                                component = component,
                                role = PMJeiRequirementRole.INPUT,
                                // Keep group index as node.index for compatibility with existing hint logic.
                                index = groupIndex,
                            )
                        }
                    }
                }

                JeiRenderOptions.CandidateSlotRenderMode.ALTERNATIVES -> {
                    // One slot per fuzzy group; JEI cycles alternatives.
                    fuzzy.forEachIndexed { i, _ ->
                        out += PMJeiRequirementNode(
                            nodeId = "${component.id}:fuzzy_input:$i",
                            type = type,
                            component = component,
                            role = PMJeiRequirementRole.INPUT,
                            index = i,
                        )
                    }
                }
            }
        }

        // Dynamic inputs
        val dynamic = component.properties[RequirementPropertyKeys.DYNAMIC_ITEM_INPUTS] as? List<DynamicItemInputGroup>
        if (!dynamic.isNullOrEmpty()) {
            when (JeiRenderOptions.current().candidateSlotRenderMode) {
                JeiRenderOptions.CandidateSlotRenderMode.EXPANDED -> {
                    dynamic.forEachIndexed { groupIndex, group ->
                        val shown = if (group.displayedCandidates.isNotEmpty()) group.displayedCandidates else listOf(group.pattern)
                        for (candidateIndex in shown.indices) {
                            out += PMJeiRequirementNode(
                                nodeId = "${component.id}:dynamic_input:$groupIndex:$candidateIndex",
                                type = type,
                                component = component,
                                role = PMJeiRequirementRole.INPUT,
                                index = groupIndex,
                            )
                        }
                    }
                }

                JeiRenderOptions.CandidateSlotRenderMode.ALTERNATIVES -> {
                    dynamic.forEachIndexed { i, _ ->
                        out += PMJeiRequirementNode(
                            nodeId = "${component.id}:dynamic_input:$i",
                            type = type,
                            component = component,
                            role = PMJeiRequirementRole.INPUT,
                            index = i,
                        )
                    }
                }
            }
        }

        // Random outputs
        @Suppress("UNCHECKED_CAST")
        val random = component.properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<ItemStack>
        if (random != null && random.pickCount > 0 && random.candidates.isNotEmpty()) {
            when (JeiRenderOptions.current().candidateSlotRenderMode) {
                JeiRenderOptions.CandidateSlotRenderMode.EXPANDED -> {
                    // Split into N slots: one per candidate.
                    // We keep pool index = 0 (single pool) in the nodeId for future extension.
                    for (candidateIndex in random.candidates.indices) {
                        out += PMJeiRequirementNode(
                            nodeId = "${component.id}:random_output:0:$candidateIndex",
                            type = type,
                            component = component,
                            role = PMJeiRequirementRole.OUTPUT,
                            index = candidateIndex,
                        )
                    }
                }

                JeiRenderOptions.CandidateSlotRenderMode.ALTERNATIVES -> {
                    // One slot per pick; JEI cycles alternatives.
                    for (i in 0 until random.pickCount) {
                        out += PMJeiRequirementNode(
                            nodeId = "${component.id}:random_output:$i",
                            type = type,
                            component = component,
                            role = PMJeiRequirementRole.OUTPUT,
                            index = i,
                        )
                    }
                }
            }
        }

        return out
    }

    override fun variants(ctx: JeiRecipeContext, node: PMJeiRequirementNode<ItemRequirementComponent>): List<PMJeiRendererVariant> {
        return variants
    }

    override fun defaultVariant(ctx: JeiRecipeContext, node: PMJeiRequirementNode<ItemRequirementComponent>): PMJeiRendererVariant {
        return variants[0]
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
        if (variant.id == PMJeiIcons.ITEM_PLAID) {
            out.add(PlaidItemWidget(PMJeiIcons.ITEM_PLAID).pos(x, y))
        } else if (variant.id.path.startsWith("plaid/base_")) {
            out.add(PlaidItemWidget(variant.id).pos(x, y))
        }
    }

    private class PlaidItemWidget(private val baseId: ResourceLocation) : Widget<PlaidItemWidget>() {
        private val baseTex: ResourceLocation = when (baseId) {
            // item/plaid is a dedicated item slot background texture
            // See: jei_recipeicons/item_module/item_module.md
            PMJeiIcons.ITEM_PLAID -> PMJeiIcons.tex("item_module/plaid_base.png")

            // plaid/base_* are 18x18 background tiles used for multi-slot groups
            // See: jei_recipeicons/plaid_module/plaid_module.md
            PMJeiIcons.PLAID_BASE_0101 -> PMJeiIcons.tex("plaid_module/base_top_0101.png")
            PMJeiIcons.PLAID_BASE_0111 -> PMJeiIcons.tex("plaid_module/base_top_0111.png")
            PMJeiIcons.PLAID_BASE_0110 -> PMJeiIcons.tex("plaid_module/base_top_0110.png")

            PMJeiIcons.PLAID_BASE_1101 -> PMJeiIcons.tex("plaid_module/base_mid_1101.png")
            PMJeiIcons.PLAID_BASE_1111 -> PMJeiIcons.tex("plaid_module/base_mid_1111.png")
            PMJeiIcons.PLAID_BASE_1110 -> PMJeiIcons.tex("plaid_module/base_mid_1110.png")

            PMJeiIcons.PLAID_BASE_1001 -> PMJeiIcons.tex("plaid_module/base_down_1001.png")
            PMJeiIcons.PLAID_BASE_1011 -> PMJeiIcons.tex("plaid_module/base_down_1011.png")
            PMJeiIcons.PLAID_BASE_1010 -> PMJeiIcons.tex("plaid_module/base_down_1010.png")

            else -> PMJeiIcons.tex("item_module/plaid_base.png")
        }
        
        // Map base to border overlay texture if applicable
        private val overlayTex: ResourceLocation? = when (baseId) {
            PMJeiIcons.ITEM_PLAID -> PMJeiIcons.tex("plaid_module/0000.png")
            PMJeiIcons.PLAID_BASE_0101 -> PMJeiIcons.tex("plaid_module/1010.png") // Top-Left
            PMJeiIcons.PLAID_BASE_0111 -> PMJeiIcons.tex("plaid_module/1000.png") // Top-Mid
            PMJeiIcons.PLAID_BASE_0110 -> PMJeiIcons.tex("plaid_module/1001.png") // Top-Right
            PMJeiIcons.PLAID_BASE_1101 -> PMJeiIcons.tex("plaid_module/0001.png") // Mid-Left
            PMJeiIcons.PLAID_BASE_1110 -> PMJeiIcons.tex("plaid_module/0010.png") // Mid-Right
            PMJeiIcons.PLAID_BASE_1001 -> PMJeiIcons.tex("plaid_module/0110.png") // Bottom-Left
            PMJeiIcons.PLAID_BASE_1011 -> PMJeiIcons.tex("plaid_module/0100.png") // Bottom-Mid
            PMJeiIcons.PLAID_BASE_1010 -> PMJeiIcons.tex("plaid_module/0101.png") // Bottom-Right
            else -> null
        }

        init { size(18, 18) }

        override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
            // 1. Draw base (18x18)
            GuiDraw.drawTexture(baseTex, 0f, 0f, 18f, 18f, 0f, 0f, 1f, 1f)
        }

        override fun drawOverlay(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
            // 2. Draw border overlay (54x54, centered at -18, -18) around the slot.
            overlayTex?.let {
                // GuiDraw.drawTexture expects x0,y0,x1,y1; so end = start + size.
                GuiDraw.drawTexture(it, -18f, -18f, 36f, 36f, 0f, 0f, 1f, 1f)
            }
        }
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
