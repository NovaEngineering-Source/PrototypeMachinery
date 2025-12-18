package github.kasuminova.prototypemachinery.integration.jei.category

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.preview.ui.StructurePreviewUiHostConfig
import github.kasuminova.prototypemachinery.client.preview.ui.StructurePreviewUiScreen
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiModularPanelRuntime
import github.kasuminova.prototypemachinery.integration.jei.wrapper.PMStructurePreviewWrapper
import mezz.jei.api.IGuiHelper
import mezz.jei.api.gui.IDrawable
import mezz.jei.api.gui.IRecipeLayout
import mezz.jei.api.ingredients.IIngredients
import mezz.jei.api.recipe.IRecipeCategory
import net.minecraft.util.ResourceLocation

/**
 * JEI category: a read-only structure preview screen embedded in the recipe GUI.
 *
 * Single category for all machine structure previews.
 */
public class PMStructurePreviewCategory(
    guiHelper: IGuiHelper,
) : IRecipeCategory<PMStructurePreviewWrapper> {

    private val uid: String = UID

    private val background: IDrawable = guiHelper.createBlankDrawable(WIDTH, HEIGHT)

    override fun getUid(): String = uid

    override fun getTitle(): String = "Structure Preview"

    override fun getModName(): String = PrototypeMachinery.MOD_NAME

    override fun getBackground(): IDrawable = background

    override fun setRecipe(
        recipeLayout: IRecipeLayout,
        recipeWrapper: PMStructurePreviewWrapper,
        ingredients: IIngredients,
    ) {
        // Build a fresh panel runtime. (The runtime is tied to the first host GuiScreen it was drawn on.)
        val panel = StructurePreviewUiScreen.createPanel(
            structureId = recipeWrapper.structureId,
            sliceCountOverride = null,
            host = StructurePreviewUiHostConfig.jeiReadOnly(),
        )

        if (panel == null) {
            PrototypeMachinery.logger.warn(
                "JEI: failed to build structure preview panel for machineType='${recipeWrapper.machineType.id}', structureId='${recipeWrapper.structureId}'"
            )
            recipeWrapper.runtime = null
            return
        }

        recipeWrapper.runtime = JeiModularPanelRuntime(
            panel = panel,
            width = WIDTH,
            height = HEIGHT,
        )
    }

    public companion object {
        public const val WIDTH: Int = 184
        public const val HEIGHT: Int = 220

        public val UID: String = ResourceLocation(PrototypeMachinery.MOD_ID, "structure_preview").toString()
    }
}
