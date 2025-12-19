package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.PrototypeMachinery
import mezz.jei.api.ingredients.IIngredientHelper
import net.minecraft.client.resources.I18n
import javax.annotation.Nullable

public object EnergyJeiIngredientHelper : IIngredientHelper<EnergyJeiIngredient> {

    public fun formatDisplayName(ingredient: EnergyJeiIngredient): String {
        val direction = formatDirection(ingredient)
        val timing = formatTiming(ingredient)
        return I18n.format("prototypemachinery.jei.energy.display", direction, timing)
    }

    public fun formatAmountLine(ingredient: EnergyJeiIngredient): String {
        return when (ingredient.timing) {
            EnergyJeiIngredient.Timing.ONCE -> I18n.format("prototypemachinery.jei.energy.amount.once", ingredient.amount)
            EnergyJeiIngredient.Timing.PER_TICK -> I18n.format("prototypemachinery.jei.energy.amount.per_tick", ingredient.amount)
        }
    }

    private fun formatDirection(ingredient: EnergyJeiIngredient): String {
        return when (ingredient.direction) {
            EnergyJeiIngredient.Direction.CONSUME -> I18n.format("prototypemachinery.jei.energy.direction.consume")
            EnergyJeiIngredient.Direction.PRODUCE -> I18n.format("prototypemachinery.jei.energy.direction.produce")
        }
    }

    private fun formatTiming(ingredient: EnergyJeiIngredient): String {
        return when (ingredient.timing) {
            EnergyJeiIngredient.Timing.ONCE -> I18n.format("prototypemachinery.jei.energy.timing.once")
            EnergyJeiIngredient.Timing.PER_TICK -> I18n.format("prototypemachinery.jei.energy.timing.per_tick")
        }
    }

    @Nullable
    override fun getMatch(
        ingredients: Iterable<EnergyJeiIngredient>,
        ingredientToMatch: EnergyJeiIngredient,
    ): EnergyJeiIngredient? {
        // Match by (direction, timing) only so users can quickly query "all consuming" / "all producing" recipes.
        return ingredients.firstOrNull {
            it.direction == ingredientToMatch.direction && it.timing == ingredientToMatch.timing
        }
    }

    override fun getDisplayName(ingredient: EnergyJeiIngredient): String {
        return formatDisplayName(ingredient)
    }

    override fun getUniqueId(ingredient: EnergyJeiIngredient): String {
        // IMPORTANT: do NOT include amount.
        // If amount is part of the id, JEI "show uses" will only match recipes with the exact same amount,
        // which usually collapses to just the current recipe.
        return "${PrototypeMachinery.MOD_ID}:pm_energy:${ingredient.direction.name.lowercase()}:${ingredient.timing.name.lowercase()}"
    }

    override fun getWildcardId(ingredient: EnergyJeiIngredient): String {
        // Same as unique id: our concept of "wildcard" is simply direction + timing.
        return "${PrototypeMachinery.MOD_ID}:pm_energy:${ingredient.direction.name.lowercase()}:${ingredient.timing.name.lowercase()}"
    }

    override fun getModId(ingredient: EnergyJeiIngredient): String {
        return PrototypeMachinery.MOD_ID
    }

    override fun getResourceId(ingredient: EnergyJeiIngredient): String {
        // JEI 1.12 typically combines (modId, resourceId). Avoid ':' here to keep it a plain "path".
        return "pm_energy"
    }

    override fun copyIngredient(ingredient: EnergyJeiIngredient): EnergyJeiIngredient {
        return ingredient
    }

    override fun getErrorInfo(@Nullable ingredient: EnergyJeiIngredient?): String {
        return ingredient?.let { "${it.direction}/${it.timing}/${it.amount}" } ?: "null"
    }
}
