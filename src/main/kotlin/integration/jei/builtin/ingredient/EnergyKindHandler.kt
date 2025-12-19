package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.IngredientsGroupKindHandlerAdapter
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds

/**
 * Ingredient group handler for energy.
 *
 * Uses a slot renderer that does not draw (so it won't cover the ModularUI energy bar),
 * but still provides tooltip + focus.
 */
public object EnergyKindHandler : IngredientsGroupKindHandlerAdapter<EnergyJeiIngredient>(
    kind = JeiSlotKinds.ENERGY,
    ingredientType = EnergyJeiType,
    ingredientRenderer = EnergyJeiIngredientSlotRenderer,
)
