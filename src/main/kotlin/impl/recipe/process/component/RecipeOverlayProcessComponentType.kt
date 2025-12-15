package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation

/**
 * Overlay for dynamic recipe modifications.
 * 动态配方修改的运行时 overlay（按 process 维度）。
 */
public object RecipeOverlayProcessComponentType : RecipeProcessComponentType<RecipeOverlayProcessComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "recipe_overlay")

    override val system: RecipeProcessSystem<RecipeOverlayProcessComponent>? = null

    override fun createComponent(process: RecipeProcess): RecipeOverlayProcessComponent = RecipeOverlayProcessComponent(process)
}
