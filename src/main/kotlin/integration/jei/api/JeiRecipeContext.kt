package github.kasuminova.prototypemachinery.integration.jei.api

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import net.minecraft.util.ResourceLocation

/**
 * JEI recipe rendering/build context.
 *
 * NOTE: This is intentionally JEI-API agnostic, so it can be used by layout compilation and
 * renderers without pulling mezz.jei classes into non-JEI code paths.
 */
public data class JeiRecipeContext(
    public val machineType: MachineType,
    public val recipe: MachineRecipe,
) {
    public val machineTypeId: ResourceLocation
        get() = machineType.id

    public val recipeId: String
        get() = recipe.id
}
