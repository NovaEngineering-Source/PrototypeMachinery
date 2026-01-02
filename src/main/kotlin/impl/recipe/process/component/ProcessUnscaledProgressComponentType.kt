package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation

/**
 * Process component type for [ProcessUnscaledProgressComponent].
 */
public object ProcessUnscaledProgressComponentType : RecipeProcessComponentType<ProcessUnscaledProgressComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "process_unscaled_progress")

    // Updated by the recipe processor system (on successful ticks).
    override val system: RecipeProcessSystem<ProcessUnscaledProgressComponent>? = null

    override fun createComponent(process: RecipeProcess): ProcessUnscaledProgressComponent = ProcessUnscaledProgressComponent(process)
}
