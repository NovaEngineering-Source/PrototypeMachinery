package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import mezz.jei.api.recipe.IIngredientType

public object EnergyJeiType : IIngredientType<EnergyJeiIngredient> {
    override fun getIngredientClass(): Class<out EnergyJeiIngredient> = EnergyJeiIngredient::class.java
}
