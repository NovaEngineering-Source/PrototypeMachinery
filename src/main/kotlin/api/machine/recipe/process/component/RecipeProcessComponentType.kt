package github.kasuminova.prototypemachinery.api.machine.recipe.process.component

import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation

public interface RecipeProcessComponentType<C : RecipeProcessComponent> {

    public val id: ResourceLocation

    public val system: RecipeProcessSystem<C>

    public fun createComponent(process: RecipeProcess): C

}