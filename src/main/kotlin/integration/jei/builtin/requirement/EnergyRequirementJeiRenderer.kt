package github.kasuminova.prototypemachinery.integration.jei.builtin.requirement

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.drawable.Stencil
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import com.cleanroommc.modularui.widgets.TextWidget
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiPlacementDataKeys
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
import github.kasuminova.prototypemachinery.integration.jei.config.PmJeiUiConfig
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

    private val graphicalVariants: List<PMJeiRendererVariant> = PMJeiIcons.ALL_VARIANTS.values
        .filter { it.id.path.startsWith("energy/") }

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
        return listOf(textVariant) + graphicalVariants
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
        // Declare a custom JEI ingredient slot for energy so recipes become searchable by
        // consume/produce and once/per-tick, and so we can attach tooltips/focus on hover.
        val role = when (node.role) {
            PMJeiRequirementRole.INPUT, PMJeiRequirementRole.INPUT_PER_TICK -> JeiSlotRole.INPUT
            PMJeiRequirementRole.OUTPUT, PMJeiRequirementRole.OUTPUT_PER_TICK -> JeiSlotRole.OUTPUT
            else -> JeiSlotRole.CATALYST
        }

        val idx = out.nextIndex(JeiSlotKinds.ENERGY)
        out.add(
            JeiSlot(
                kind = JeiSlotKinds.ENERGY,
                nodeId = node.nodeId,
                index = idx,
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
        node: PMJeiRequirementNode<EnergyRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        out: PMJeiWidgetCollector,
    ) {
        buildWidgetsWithData(ctx, node, variant, x, y, emptyMap(), out)
    }

    override fun buildWidgetsWithData(
        ctx: JeiRecipeContext,
        node: PMJeiRequirementNode<EnergyRequirementComponent>,
        variant: PMJeiRendererVariant,
        x: Int,
        y: Int,
        data: Map<String, Any>,
        out: PMJeiWidgetCollector,
    ) {
        if (variant.id.path.startsWith("energy/")) {
            // Energy module visuals are based on an "empty" background and a "full" overlay with a fill mask.
            // For JEI (no real capacity context), we render a stable demo fill ratio per role.
            val demoFillRatio = when (node.role) {
                PMJeiRequirementRole.INPUT, PMJeiRequirementRole.INPUT_PER_TICK -> 0.65f
                PMJeiRequirementRole.OUTPUT, PMJeiRequirementRole.OUTPUT_PER_TICK -> 0.35f
                else -> 0.5f
            }

            val ledYOffsetOverride = (data[PMJeiPlacementDataKeys.ENERGY_LED_Y_OFFSET] as? Number)?.toInt()
            out.add(EnergyModuleWidget(variant, node.role, demoFillRatio, ledYOffsetOverride).pos(x, y))
        } else {
            val text = formatEnergyText(node)
            out.add(
                TextWidget(IKey.str(text))
                    .pos(x, y)
            )
        }
    }

    private class EnergyModuleWidget(
        private val variant: PMJeiRendererVariant,
        private val role: PMJeiRequirementRole,
        /** 0..1, where 0 means empty and 1 means full. */
        private val fillRatio: Float,
        private val ledYOffsetOverride: Int?,
    ) : Widget<EnergyModuleWidget>() {

        private companion object {
            private const val LED_W: Int = 10
            private const val LED_H: Int = 1
        }

        private val emptyTex: ResourceLocation
        private val fullTex: ResourceLocation
        private val ledTex: ResourceLocation

        init {
            val suffix = variant.id.path.removePrefix("energy/")
            // energy_module uses flat filenames like: 1_3_empty.png / 1_3_full.png
            // default uses: default_in_empty.png / default_in_full.png
            val key = when (suffix) {
                "default" -> "default_in"
                else -> suffix.replace("x", "_")
            }
            emptyTex = PMJeiIcons.tex("energy_module/${key}_empty.png")
            fullTex = PMJeiIcons.tex("energy_module/${key}_full.png")

            // IO marker (spec: energy_module.md)
            // Placed at the very top edge and centered horizontally.
            ledTex = when (role) {
                PMJeiRequirementRole.INPUT, PMJeiRequirementRole.INPUT_PER_TICK -> PMJeiIcons.tex("energy_module/input_led.png")
                PMJeiRequirementRole.OUTPUT, PMJeiRequirementRole.OUTPUT_PER_TICK -> PMJeiIcons.tex("energy_module/output_led.png")
                else -> PMJeiIcons.tex("energy_module/io_led.png")
            }

            size(variant.width, variant.height)
        }

        override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
            // 1) Empty background
            GuiDraw.drawTexture(emptyTex, 0f, 0f, area.width.toFloat(), area.height.toFloat(), 0f, 0f, 1f, 1f)

            // 2) Full overlay, clipped by the fill mask (spec: energy_module.md)
            // The "bar" content rect is inset by 3px from each side.
            val insetX = 3
            val insetY = 3
            val insetW = (area.width - 6).coerceAtLeast(1)
            val insetH = (area.height - 6).coerceAtLeast(1)

            val ratio = fillRatio.coerceIn(0f, 1f)
            val filledH = (insetH * ratio).toInt().coerceIn(0, insetH)
            if (filledH <= 0) return

            // Fill grows from bottom to top.
            val clipX = insetX
            val clipY = insetY + (insetH - filledH)

            Stencil.applyTransformed(clipX, clipY, insetW, filledH)
            try {
                GuiDraw.drawTexture(fullTex, 0f, 0f, area.width.toFloat(), area.height.toFloat(), 0f, 0f, 1f, 1f)
            } finally {
                Stencil.remove()
            }

            // 3) IO led at top, centered.
            val ledX0 = ((area.width - LED_W) / 2f)
            val ledY0 = (ledYOffsetOverride ?: PmJeiUiConfig.ui.energyLedYOffset).toFloat()
            GuiDraw.drawTexture(
                ledTex,
                ledX0,
                ledY0,
                ledX0 + LED_W,
                ledY0 + LED_H,
                0f,
                0f,
                1f,
                1f,
            )
        }
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
