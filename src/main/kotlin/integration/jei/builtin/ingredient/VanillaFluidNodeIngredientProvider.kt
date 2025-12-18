package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.FluidRequirementJeiRenderer
import net.minecraftforge.fluids.FluidStack

public object VanillaFluidNodeIngredientProvider : PMJeiNodeIngredientProvider<FluidRequirementComponent, FluidStack> {

    override val type: github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType<FluidRequirementComponent> = RecipeRequirementTypes.FLUID

    override val kind: JeiSlotKind = JeiSlotKinds.FLUID

    override fun getDisplayed(ctx: JeiRecipeContext, node: PMJeiRequirementNode<FluidRequirementComponent>): List<FluidStack> {
        return FluidRequirementJeiRenderer.getDisplayedFluids(node)
    }
}
