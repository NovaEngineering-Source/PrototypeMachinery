package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation

/**
 * Process component type for [RequirementResolutionProcessComponent].
 */
public object RequirementResolutionProcessComponentType : RecipeProcessComponentType<RequirementResolutionProcessComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "requirement_resolution")

    override val system: RecipeProcessSystem<RequirementResolutionProcessComponent>? = null

    override fun createComponent(process: RecipeProcess): RequirementResolutionProcessComponent =
        RequirementResolutionProcessComponent(process)
}
