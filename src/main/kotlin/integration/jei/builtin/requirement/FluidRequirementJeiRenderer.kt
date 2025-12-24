package github.kasuminova.prototypemachinery.integration.jei.builtin.requirement

import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotCollector
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotOverlay
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRendererVariant
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiIcons
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiRenderOptions
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
    ) + PMJeiIcons.ALL_VARIANTS.values.filter { it.id.path.startsWith("fluid/") }

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

        // Fuzzy inputs
        @Suppress("UNCHECKED_CAST")
        val fuzzy = component.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<FluidStack>>
        if (!fuzzy.isNullOrEmpty()) {
            when (JeiRenderOptions.current().candidateSlotRenderMode) {
                JeiRenderOptions.CandidateSlotRenderMode.EXPANDED -> {
                    for (groupIndex in fuzzy.indices) {
                        val group = fuzzy[groupIndex]
                        for (candidateIndex in group.candidates.indices) {
                            out += PMJeiRequirementNode(
                                nodeId = "${component.id}:fuzzy_input:$groupIndex:$candidateIndex",
                                type = type,
                                component = component,
                                role = PMJeiRequirementRole.INPUT,
                                index = groupIndex,
                            )
                        }
                    }
                }

                JeiRenderOptions.CandidateSlotRenderMode.ALTERNATIVES -> {
                    for (i in fuzzy.indices) {
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

        // Random outputs
        @Suppress("UNCHECKED_CAST")
        val random = component.properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<FluidStack>
        if (random != null && random.pickCount > 0 && random.candidates.isNotEmpty()) {
            when (JeiRenderOptions.current().candidateSlotRenderMode) {
                JeiRenderOptions.CandidateSlotRenderMode.EXPANDED -> {
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

        // Fluid module spec:
        // - base is drawn behind the fluid
        // - fluid is rendered at (1,1,w-2,h-2)
        // - top is drawn above the fluid, and contains the visible border/frame
        // (see: jei_recipeicons/fluid_module/fluid_module.md)
        //
        // In JEI, the "overlay" drawable is clipped to the declared slot bounds.
        // Therefore, to keep the frame visible, we declare the slot at FULL module size
        // and use the full *_top.png as the overlay (no 1px cropping).
        val isModuleVariant = variant.id.path.startsWith("fluid/")
        val slotX = x
        val slotY = y
        val slotW = variant.width
        val slotH = variant.height

        val overlay: JeiSlotOverlay? = if (isModuleVariant) {
            val overlayTex = resolveFluidModuleOverlayTex(variant)
            JeiSlotOverlay(
                texture = overlayTex,
                u = 0,
                v = 0,
                width = variant.width,
                height = variant.height,
                textureWidth = variant.width,
                textureHeight = variant.height,
            )
        } else null

        val index = out.nextIndex(JeiSlotKinds.FLUID)
        out.add(
            JeiSlot(
                kind = JeiSlotKinds.FLUID,
                nodeId = node.nodeId,
                index = index,
                role = role,
                x = slotX,
                y = slotY,
                width = slotW,
                height = slotH,
                overlay = overlay,
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
        if (variant.id.path.startsWith("fluid/")) {
            out.add(FluidModuleWidget(variant).pos(x, y))
        }
    }

    private class FluidModuleWidget(
        private val variant: PMJeiRendererVariant
    ) : Widget<FluidModuleWidget>() {

        private val baseTex: ResourceLocation

        init {
            val key = when (variant.id) {
                PMJeiIcons.FLUID_PLAID_1X1 -> "1_1_plaid"
                else -> variant.id.path
                    .removePrefix("fluid/")
                    .replace("x", "_")
            }
            // Most fluid module textures live under `fluid_module/`.
            // However, the 0.5x1 preset (0o5_1_*) is currently shipped under `gas_module/`.
            // We keep a small fallback here so `fluid/0o5x1` does not hard-fail at runtime.
            val moduleDir = if (key == "0o5_1") "gas_module" else "fluid_module"
            baseTex = PMJeiIcons.tex("$moduleDir/${key}_base.png")

            size(variant.width, variant.height)
        }

        override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
            // Base/frame goes behind the JEI-rendered fluid.
            GuiDraw.drawTexture(baseTex, 0f, 0f, area.width.toFloat(), area.height.toFloat(), 0f, 0f, 1f, 1f)
        }
    }

    private fun resolveFluidModuleOverlayTex(variant: PMJeiRendererVariant): ResourceLocation {
        val key = when (variant.id) {
            PMJeiIcons.FLUID_PLAID_1X1 -> "1_1_plaid"
            else -> variant.id.path
                .removePrefix("fluid/")
                .replace("x", "_")
        }
        val moduleDir = if (key == "0o5_1") "gas_module" else "fluid_module"
        return PMJeiIcons.tex("$moduleDir/${key}_top.png")
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
