package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode

public object EnergyNodeIngredientProvider : PMJeiNodeIngredientProvider<EnergyRequirementComponent, EnergyJeiIngredient> {

    override val type: RecipeRequirementType<EnergyRequirementComponent> = RecipeRequirementTypes.ENERGY

    override val kind: JeiSlotKind = JeiSlotKinds.ENERGY

    override fun getDisplayed(ctx: JeiRecipeContext, node: PMJeiRequirementNode<EnergyRequirementComponent>): List<EnergyJeiIngredient> {
        val c = node.component
        return when (node.role) {
            PMJeiRequirementRole.INPUT -> listOf(EnergyJeiIngredient.consumeOnce(c.input.coerceAtLeast(0L)))
            PMJeiRequirementRole.OUTPUT -> listOf(EnergyJeiIngredient.produceOnce(c.output.coerceAtLeast(0L)))
            PMJeiRequirementRole.INPUT_PER_TICK -> listOf(EnergyJeiIngredient.consumePerTick(c.inputPerTick.coerceAtLeast(0L)))
            PMJeiRequirementRole.OUTPUT_PER_TICK -> listOf(EnergyJeiIngredient.producePerTick(c.outputPerTick.coerceAtLeast(0L)))
            else -> emptyList()
        }
    }
}
