package github.kasuminova.prototypemachinery.integration.jei.wrapper

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiModularPanelRuntime
import github.kasuminova.prototypemachinery.integration.jei.util.JeiControllerStack
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.ingredients.VanillaTypes
import mezz.jei.api.recipe.IRecipeWrapper
import net.minecraft.client.Minecraft

/**
 * Virtual JEI recipe wrapper: structure preview for a single [MachineType].
 */
public class PMStructurePreviewWrapper(
    public val machineType: MachineType,
    public val structureId: String,
) : IRecipeWrapper {

    /** Set by category#setRecipe. */
    @Volatile
    public var runtime: JeiModularPanelRuntime? = null

    override fun getIngredients(ingredients: IIngredients) {
        // Allow opening this category via the *recipe direction* (JEI default key: R) on the controller.
        // No slot is declared; the preview panel is fully custom.
        JeiControllerStack.get(machineType)?.let { controller ->
            ingredients.setOutput(VanillaTypes.ITEM, controller)
        }
    }

    override fun drawInfo(
        minecraft: Minecraft,
        recipeWidth: Int,
        recipeHeight: Int,
        mouseX: Int,
        mouseY: Int,
    ) {
        // JEI translates the GL matrix to the recipe origin before calling drawInfo.
        runtime?.drawAt(0, 0, mouseX, mouseY, minecraft.renderPartialTicks)
    }
}
