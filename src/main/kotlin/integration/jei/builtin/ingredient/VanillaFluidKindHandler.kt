package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotOverlay
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.FluidRequirementJeiRenderer
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.gui.ITooltipCallback
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IIngredientType
import net.minecraft.client.resources.I18n
import net.minecraftforge.fluids.FluidStack
import java.util.Collections
import java.util.WeakHashMap

public object VanillaFluidKindHandler : PMJeiIngredientKindHandler<FluidStack> {

    private data class FluidIoHint(
        val directionKey: String,
        val timingKey: String,
    )

    // One tooltip callback per recipe layout. We keep per-layout slot hints in weak maps.
    private val hookedLayouts: MutableSet<IRecipeLayout> = Collections.newSetFromMap(WeakHashMap())
    private val hintByLayout: MutableMap<IRecipeLayout, MutableMap<Int, FluidIoHint>> = Collections.synchronizedMap(WeakHashMap())

    override val kind: JeiSlotKind = JeiSlotKinds.FLUID

    override val ingredientType: IIngredientType<FluidStack> = VanillaTypes.FLUID

    override fun init(recipeLayout: IRecipeLayout, guiHelper: IGuiHelper, slot: JeiSlot, node: PMJeiRequirementNode<*>?) {
        // Attach tooltip callback once per layout, and keep a map from slotIndex -> (consume/produce, once/per-tick).
        if (hookedLayouts.add(recipeLayout)) {
            recipeLayout.fluidStacks.addTooltipCallback(object : ITooltipCallback<FluidStack> {
                override fun onTooltip(slotIndex: Int, input: Boolean, ingredient: FluidStack, tooltip: MutableList<String>) {
                    val hint = hintByLayout[recipeLayout]?.get(slotIndex) ?: return
                    val line = I18n.format(
                        "prototypemachinery.jei.fluid.tooltip.io",
                        I18n.format(hint.directionKey),
                        I18n.format(hint.timingKey),
                    )

                    // Insert after the fluid name if possible; otherwise append.
                    val insertAt = if (tooltip.isNotEmpty()) 1 else 0
                    if (insertAt >= 0 && insertAt <= tooltip.size) {
                        tooltip.add(insertAt, line)
                    } else {
                        tooltip.add(line)
                    }
                }
            })
        }

        // Record IO/timing hint for this slot if we have a requirement node.
        node?.let {
            val hint = when (it.role) {
                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.INPUT ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.consume",
                        timingKey = "prototypemachinery.jei.energy.timing.once",
                    )

                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.OUTPUT ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.produce",
                        timingKey = "prototypemachinery.jei.energy.timing.once",
                    )

                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.INPUT_PER_TICK ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.consume",
                        timingKey = "prototypemachinery.jei.energy.timing.per_tick",
                    )

                github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole.OUTPUT_PER_TICK ->
                    FluidIoHint(
                        directionKey = "prototypemachinery.jei.energy.direction.produce",
                        timingKey = "prototypemachinery.jei.energy.timing.per_tick",
                    )

                else -> null
            }

            if (hint != null) {
                val map = hintByLayout.getOrPut(recipeLayout) { HashMap() }
                map[slot.index] = hint
            }
        }

        val cap = run {
            val n = node
            val comp = n?.component as? FluidRequirementComponent
            if (n != null && comp != null) {
                @Suppress("UNCHECKED_CAST")
                val cast = n as PMJeiRequirementNode<FluidRequirementComponent>
                FluidRequirementJeiRenderer.getCapacityMb(cast)
            } else {
                1000
            }
        }

        val overlayDrawable = slot.overlay?.toDrawable(guiHelper)

        recipeLayout.fluidStacks.init(
            slot.index,
            slot.role == github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole.INPUT,
            slot.x,
            slot.y,
            slot.width,
            slot.height,
            cap,
            false,
            // If we have a custom overlay (e.g. module scale marks/glass), use it.
            // Otherwise fall back to JEI's default slot overlay for legacy tank variants.
            overlayDrawable ?: guiHelper.slotDrawable,
        )
    }

    override fun set(recipeLayout: IRecipeLayout, slot: JeiSlot, values: List<FluidStack>) {
        recipeLayout.fluidStacks.set(slot.index, values)
    }
}

private fun JeiSlotOverlay.toDrawable(guiHelper: IGuiHelper): mezz.jei.api.gui.IDrawable {
    // IMPORTANT: these module textures are not 256x256; provide the real texture size.
    return guiHelper.drawableBuilder(texture, u, v, width, height)
        .setTextureSize(textureWidth, textureHeight)
        .build()
}
