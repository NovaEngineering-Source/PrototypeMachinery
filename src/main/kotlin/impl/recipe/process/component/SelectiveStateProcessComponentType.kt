package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.ecs.ComponentSystem
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
public object SelectiveStateProcessComponentType : RecipeProcessComponentType<SelectiveStateProcessComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "selective_state")

    override val system: RecipeProcessSystem<SelectiveStateProcessComponent> = object : RecipeProcessSystem<SelectiveStateProcessComponent>,
        ComponentSystem<RecipeProcess, SelectiveStateProcessComponent> {
        override fun onPreTick(entity: RecipeProcess, component: SelectiveStateProcessComponent) {}
        override fun onTick(entity: RecipeProcess, component: SelectiveStateProcessComponent) {}
        override fun onPostTick(entity: RecipeProcess, component: SelectiveStateProcessComponent) {}
    }

    override fun createComponent(process: RecipeProcess): SelectiveStateProcessComponent = SelectiveStateProcessComponent(process)

}
