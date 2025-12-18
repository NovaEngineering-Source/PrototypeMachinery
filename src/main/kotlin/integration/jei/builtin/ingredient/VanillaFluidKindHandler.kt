package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiIngredientKindHandler
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlot
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.FluidRequirementJeiRenderer
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IIngredientType
import net.minecraftforge.fluids.FluidStack

public object VanillaFluidKindHandler : PMJeiIngredientKindHandler<FluidStack> {

    override val kind: JeiSlotKind = JeiSlotKinds.FLUID

    override val ingredientType: IIngredientType<FluidStack> = VanillaTypes.FLUID

    override fun init(recipeLayout: IRecipeLayout, guiHelper: IGuiHelper, slot: JeiSlot, node: PMJeiRequirementNode<*>?) {
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

        recipeLayout.fluidStacks.init(
            slot.index,
            slot.role == github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole.INPUT,
            slot.x,
            slot.y,
            slot.width,
            slot.height,
            cap,
            false,
            guiHelper.slotDrawable,
        )
    }

    override fun set(recipeLayout: IRecipeLayout, slot: JeiSlot, values: List<FluidStack>) {
        recipeLayout.fluidStacks.set(slot.index, values)
    }
}
