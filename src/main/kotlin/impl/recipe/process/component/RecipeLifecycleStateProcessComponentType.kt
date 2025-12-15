package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation

/**
 * Lifecycle state component type.
 * 生命周期状态组件类型。
 */
public object RecipeLifecycleStateProcessComponentType : RecipeProcessComponentType<RecipeLifecycleStateProcessComponent> {

    override val id: ResourceLocation = ResourceLocation("prototypemachinery", "process_lifecycle")

    // No-op system; state is updated by the processor.
    override val system: RecipeProcessSystem<RecipeLifecycleStateProcessComponent>? = null

    override fun createComponent(process: RecipeProcess): RecipeLifecycleStateProcessComponent = RecipeLifecycleStateProcessComponent(process)
}
