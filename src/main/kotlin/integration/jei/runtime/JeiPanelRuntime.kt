package github.kasuminova.prototypemachinery.integration.jei.runtime

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.screen.UISettings
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiDecoratorPlacement
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiFixedSlotPlacement
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutBuilder
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutRequirementsView
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiMachineLayoutDefinition
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiPlacedNode
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotCollector
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRendererVariant
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementRenderer
import github.kasuminova.prototypemachinery.integration.jei.api.ui.PMJeiWidgetCollector
import github.kasuminova.prototypemachinery.integration.jei.builtin.JeiBackgroundSpec
import github.kasuminova.prototypemachinery.integration.jei.builtin.PMJeiIcons
import github.kasuminova.prototypemachinery.integration.jei.builtin.widget.JeiNineSliceBackgroundWidget
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiDecoratorRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiFixedSlotProviderRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiMachineLayoutRegistry
import github.kasuminova.prototypemachinery.integration.jei.registry.JeiRequirementRendererRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.util.ResourceLocation

/**
 * Runtime bridge: builds a ModularUI panel for a JEI recipe layout and collects JEI ingredient slot declarations.
 *
 * This is the minimal building block for the JEI category:
 * - setRecipe: init JEI ingredient groups from [slots]
 * - draw: render the ModularUI panel (background frames, texts, decorators)
 */
public class JeiPanelRuntime private constructor(
    private val ctx: JeiRecipeContext,
    public val width: Int,
    public val height: Int,
    public val slots: List<JeiSlot>,
    private val widgets: List<IWidget>,
    private val nodeById: Map<String, PMJeiRequirementNode<out RecipeRequirementComponent>>,
    private val fixedValuesBySlotNodeId: Map<String, List<Any>>,
) {

    private val panel: ModularPanel = ModularPanel.defaultPanel("pm_jei_panel")
        .size(width, height)
        // JEI draws at arbitrary origins; we will position this panel explicitly per draw call.
        .pos(0, 0)

    private val screen: ModularScreen = ModularScreen("prototypemachinery", panel)

    private var constructedOverlayFor: GuiScreen? = null
    private var lastScaledW: Int = -1
    private var lastScaledH: Int = -1

    init {
        // Important: without settings, ModularGuiContext#getUISettings may throw when queried by other systems.
        screen.context.setSettings(UISettings())

        // Attach widgets to panel.
        for (w in widgets) {
            panel.child(w)
        }
    }

    /**
     * Ensure the underlying ModularUI screen has a valid overlay wrapper and viewport size.
     */
    private fun ensureOverlay(host: GuiScreen): Boolean {
        if (constructedOverlayFor == null) {
            // NOTE: ModularScreen can only be constructed once.
            screen.constructOverlay(host)
            constructedOverlayFor = host
            lastScaledW = -1
            lastScaledH = -1
        } else if (constructedOverlayFor !== host) {
            // This runtime instance is tied to the GUI screen it was first drawn on.
            // If JEI keeps the same recipe runtime around across GUI changes, we must avoid crashing.
            PrototypeMachinery.logger.warn(
                "JEI: attempted to draw a cached JeiPanelRuntime on a different GuiScreen. " +
                    "Expected='${constructedOverlayFor!!::class.java.name}', actual='${host::class.java.name}'. " +
                    "Skipping draw; runtime should be rebuilt by JEI."
            )
            return false
        }

        val mc = Minecraft.getMinecraft()
        val sr = ScaledResolution(mc)
        val sw = sr.scaledWidth
        val sh = sr.scaledHeight

        if (sw != lastScaledW || sh != lastScaledH) {
            screen.onResize(sw, sh)
            lastScaledW = sw
            lastScaledH = sh
        }

        return true
    }

    /**
     * Draw this runtime at the given absolute screen origin (top-left of JEI recipe background).
     */
    public fun drawAt(
        originX: Int,
        originY: Int,
        mouseX: Int,
        mouseY: Int,
        partialTicks: Float,
    ) {
        val mc = Minecraft.getMinecraft()
        val host = mc.currentScreen ?: return
        if (!ensureOverlay(host)) return

        // Position the panel at JEI background origin.
        panel.pos(originX, originY)

        // Update context mouse state for hover effects/tooltips.
        screen.context.updateState(mouseX, mouseY, partialTicks)

        screen.drawScreen()
        screen.drawForeground()
    }

    /**
     * Resolve a previously built requirement node by id.
     *
     * Used by the JEI category to populate ingredient groups.
     */
    public fun getNode(nodeId: String): PMJeiRequirementNode<out RecipeRequirementComponent>? {
        return nodeById[nodeId]
    }

    /** Resolve fixed (node-less) slot values by the internal slot nodeId. */
    public fun getFixedValues(slotNodeId: String): List<Any>? {
        return fixedValuesBySlotNodeId[slotNodeId]
    }

    public companion object {

        /**
         * Build a runtime for the given recipe.
         *
         * @return null if no layout can be resolved.
         */
        public fun build(ctx: JeiRecipeContext): JeiPanelRuntime? {
            val layout = JeiMachineLayoutRegistry.resolve(ctx.machineTypeId)
            if (layout == null) {
                PrototypeMachinery.logger.warn("JEI: no layout registered for machineType '${ctx.machineTypeId}' and no default layout set")
                return null
            }
            return build(ctx, layout)
        }

        public fun build(ctx: JeiRecipeContext, layout: PMJeiMachineLayoutDefinition): JeiPanelRuntime {
            // 1) Resolve requirement nodes via renderers.
            val nodes = mutableListOf<PMJeiRequirementNode<out RecipeRequirementComponent>>()
            for ((type, list) in ctx.recipe.requirements) {
                val renderer = JeiRequirementRendererRegistry.getUnsafe(type) ?: continue
                for (component in list) {
                    val split = renderer.splitUnsafe(ctx, component)
                    nodes.addAll(split)
                }
            }

            val requirementsView = RequirementsView(nodes)

            // 2) Build layout plan.
            val planBuilder = PlanBuilder()
            layout.build(ctx, requirementsView, planBuilder)

            // 3) Materialize plan: slots + widgets.
            val slotCollector = SlotCollector()
            val widgetCollector = WidgetCollector()

            // 3.0) Background (9-slice). Drawn behind all other widgets.
            // Default uses a fixed 2px border; callers may override via a reserved decorator id.
            val bgPlacement = planBuilder.decorators.firstOrNull { it.decoratorId == JeiBackgroundSpec.id }
            val bgSpec = if (bgPlacement != null) {
                // Consume so it won't go through the normal decorator registry.
                planBuilder.decorators.remove(bgPlacement)
                JeiBackgroundSpec.parse(bgPlacement.data)
            } else {
                JeiBackgroundSpec.Spec(
                    texture = PMJeiIcons.tex("jei_base.png"),
                    borderPx = 2,
                    fillCenter = false,
                )
            }

            widgetCollector.add(
                JeiNineSliceBackgroundWidget(
                    texture = bgSpec.texture,
                    cornerPx = bgSpec.borderPx,
                    fillCenter = bgSpec.fillCenter,
                    splitMode = JeiNineSliceBackgroundWidget.SplitMode.AUTO_PIXELS,
                ).size(layout.width, layout.height)
            )

            // Build a fast node lookup.
            val nodeById = nodes.associateBy { it.nodeId }

            val fixedValuesBySlotNodeId: MutableMap<String, List<Any>> = LinkedHashMap()

            for (placement in planBuilder.placedNodes) {
                val node = nodeById[placement.nodeId]
                if (node == null) {
                    PrototypeMachinery.logger.warn(
                        "JEI layout '${layout::class.java.name}' placed unknown nodeId='${placement.nodeId}' for recipe '${ctx.recipeId}'."
                    )
                    continue
                }

                val renderer = JeiRequirementRendererRegistry.getUnsafe(node.type)
                if (renderer == null) {
                    PrototypeMachinery.logger.warn(
                        "JEI: missing renderer for requirementType='${node.type.id}' (nodeId='${node.nodeId}', recipe='${ctx.recipeId}')."
                    )
                    continue
                }

                @Suppress("UNCHECKED_CAST")
                val castNode = node as PMJeiRequirementNode<RecipeRequirementComponent>

                val variant = selectVariant(ctx, renderer, castNode, placement)

                renderer.declareJeiSlotsUnsafe(ctx, castNode, variant, placement.x, placement.y, slotCollector)
                renderer.buildWidgetsUnsafe(ctx, castNode, variant, placement.x, placement.y, placement.data, widgetCollector)
            }

            for (decor in planBuilder.decorators) {
                val decorator = JeiDecoratorRegistry.get(decor.decoratorId)
                if (decorator == null) {
                    PrototypeMachinery.logger.warn(
                        "JEI: missing decorator '${decor.decoratorId}' (recipe='${ctx.recipeId}')."
                    )
                    continue
                }
                decorator.buildWidgets(ctx, decor.x, decor.y, decor.data, widgetCollector)
            }

            // Materialize fixed slots (node-less JEI ingredient slots).
            for ((i, fixed) in planBuilder.fixedSlots.withIndex()) {
                val provider = JeiFixedSlotProviderRegistry.get(fixed.providerId)
                if (provider == null) {
                    PrototypeMachinery.logger.warn(
                        "JEI: missing fixed slot provider '${fixed.providerId}' (recipe='${ctx.recipeId}')."
                    )
                    continue
                }

                val kind = SimpleKind(provider.kindId)
                val w = fixed.width
                val h = fixed.height
                if (w <= 0 || h <= 0) {
                    PrototypeMachinery.logger.warn(
                        "JEI: fixed slot '${fixed.providerId}' has invalid size (${w}x${h}) (recipe='${ctx.recipeId}')."
                    )
                    continue
                }

                val slotNodeId = "__fixed:${fixed.providerId.namespace}:${fixed.providerId.path}:$i"
                val values = try {
                    provider.getDisplayed(ctx)
                } catch (t: Throwable) {
                    PrototypeMachinery.logger.error(
                        "JEI: fixed slot provider '${fixed.providerId}' failed for recipe='${ctx.recipeId}'.",
                        t
                    )
                    emptyList()
                }

                val slot = JeiSlot(
                    kind = kind,
                    nodeId = slotNodeId,
                    index = slotCollector.nextIndex(kind),
                    role = fixed.role,
                    x = fixed.x,
                    y = fixed.y,
                    width = w,
                    height = h,
                )

                slotCollector.add(slot)
                fixedValuesBySlotNodeId[slotNodeId] = values
            }

            return JeiPanelRuntime(
                ctx = ctx,
                width = layout.width,
                height = layout.height,
                slots = slotCollector.slots,
                widgets = widgetCollector.widgets,
                nodeById = nodeById,
                fixedValuesBySlotNodeId = fixedValuesBySlotNodeId,
            )
        }

        private fun <C : RecipeRequirementComponent> selectVariant(
            ctx: JeiRecipeContext,
            renderer: PMJeiRequirementRenderer<C>,
            node: PMJeiRequirementNode<C>,
            placement: PMJeiPlacedNode,
        ): PMJeiRendererVariant {
            val requested = placement.variantId
            if (requested != null) {
                val candidates = renderer.variants(ctx, node)
                val found = candidates.firstOrNull { it.id == requested }
                if (found != null) return found
                PrototypeMachinery.logger.warn(
                    "JEI: layout requested unknown variant '$requested' for nodeId='${node.nodeId}', type='${node.type.id}'. Falling back."
                )
            }
            return renderer.defaultVariant(ctx, node)
        }
    }

    // --- Internal helpers ---

    private class PlanBuilder : PMJeiLayoutBuilder {
        val placedNodes: MutableList<PMJeiPlacedNode> = ArrayList()
        val decorators: MutableList<PMJeiDecoratorPlacement> = ArrayList()
        val fixedSlots: MutableList<PMJeiFixedSlotPlacement> = ArrayList()

        override fun placeNode(nodeId: String, x: Int, y: Int, variantId: net.minecraft.util.ResourceLocation?) {
            placeNodeWithData(nodeId = nodeId, x = x, y = y, variantId = variantId, data = emptyMap())
        }

        override fun placeNodeWithData(
            nodeId: String,
            x: Int,
            y: Int,
            variantId: net.minecraft.util.ResourceLocation?,
            data: Map<String, Any>,
        ) {
            placedNodes += PMJeiPlacedNode(nodeId = nodeId, x = x, y = y, variantId = variantId, data = data)
        }

        override fun addDecorator(
            decoratorId: net.minecraft.util.ResourceLocation,
            x: Int,
            y: Int,
            data: Map<String, Any>,
        ) {
            decorators += PMJeiDecoratorPlacement(decoratorId = decoratorId, x = x, y = y, data = data)
        }

        override fun placeFixedSlot(
            providerId: ResourceLocation,
            role: JeiSlotRole,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            fixedSlots += PMJeiFixedSlotPlacement(
                providerId = providerId,
                role = role,
                x = x,
                y = y,
                width = width,
                height = height,
            )
        }
    }

    private data class SimpleKind(override val id: ResourceLocation) : JeiSlotKind

    private class RequirementsView(
        override val all: List<PMJeiRequirementNode<out RecipeRequirementComponent>>,
    ) : PMJeiLayoutRequirementsView {

        private val byType: Map<RecipeRequirementType<*>, List<PMJeiRequirementNode<out RecipeRequirementComponent>>> =
            all.groupBy { it.type }

        private val byRole: Map<PMJeiRequirementRole, List<PMJeiRequirementNode<out RecipeRequirementComponent>>> =
            all.groupBy { it.role }

        override fun byType(type: RecipeRequirementType<*>): List<PMJeiRequirementNode<out RecipeRequirementComponent>> {
            return byType[type].orEmpty()
        }

        override fun byRole(role: PMJeiRequirementRole): List<PMJeiRequirementNode<out RecipeRequirementComponent>> {
            return byRole[role].orEmpty()
        }
    }

    private class SlotCollector : JeiSlotCollector {
        val slots: MutableList<JeiSlot> = ArrayList()

        private val nextByKindId: MutableMap<net.minecraft.util.ResourceLocation, Int> = HashMap()

        override fun nextIndex(kind: github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind): Int {
            val id = kind.id
            val next = nextByKindId[id] ?: 0
            nextByKindId[id] = next + 1
            return next
        }

        override fun add(slot: JeiSlot) {
            slots += slot
        }
    }

    private class WidgetCollector : PMJeiWidgetCollector {
        val widgets: MutableList<IWidget> = ArrayList()

        override fun add(widget: Any) {
            val w = widget as? IWidget
            if (w == null) {
                throw IllegalArgumentException(
                    "JEI widget collector expects ModularUI IWidget, got: ${widget::class.java.name}"
                )
            }
            widgets += w
        }
    }
}

// --- Unsafe casting helpers ---

private fun JeiRequirementRendererRegistry.getUnsafe(type: RecipeRequirementType<*>): PMJeiRequirementRenderer<RecipeRequirementComponent>? {
    @Suppress("UNCHECKED_CAST")
    return (get(type as RecipeRequirementType<RecipeRequirementComponent>))
}

private fun PMJeiRequirementRenderer<RecipeRequirementComponent>.splitUnsafe(
    ctx: JeiRecipeContext,
    component: RecipeRequirementComponent,
): List<PMJeiRequirementNode<out RecipeRequirementComponent>> {
    return try {
        split(ctx, component)
    } catch (t: Throwable) {
        PrototypeMachinery.logger.error(
            "JEI: renderer split failed for type='${type.id}', recipe='${ctx.recipeId}'",
            t
        )
        emptyList()
    }
}

private fun PMJeiRequirementRenderer<RecipeRequirementComponent>.declareJeiSlotsUnsafe(
    ctx: JeiRecipeContext,
    node: PMJeiRequirementNode<out RecipeRequirementComponent>,
    variant: PMJeiRendererVariant,
    x: Int,
    y: Int,
    out: JeiSlotCollector,
) {
    try {
        @Suppress("UNCHECKED_CAST")
        declareJeiSlots(ctx, node as PMJeiRequirementNode<RecipeRequirementComponent>, variant, x, y, out)
    } catch (t: Throwable) {
        PrototypeMachinery.logger.error(
            "JEI: renderer declareJeiSlots failed for type='${type.id}', nodeId='${node.nodeId}', recipe='${ctx.recipeId}'",
            t
        )
    }
}

private fun PMJeiRequirementRenderer<RecipeRequirementComponent>.buildWidgetsUnsafe(
    ctx: JeiRecipeContext,
    node: PMJeiRequirementNode<out RecipeRequirementComponent>,
    variant: PMJeiRendererVariant,
    x: Int,
    y: Int,
    data: Map<String, Any>,
    out: PMJeiWidgetCollector,
) {
    try {
        @Suppress("UNCHECKED_CAST")
        buildWidgetsWithData(
            ctx,
            node as PMJeiRequirementNode<RecipeRequirementComponent>,
            variant,
            x,
            y,
            data,
            out
        )
    } catch (t: Throwable) {
        PrototypeMachinery.logger.error(
            "JEI: renderer buildWidgets failed for type='${type.id}', nodeId='${node.nodeId}', recipe='${ctx.recipeId}'",
            t
        )
    }
}
