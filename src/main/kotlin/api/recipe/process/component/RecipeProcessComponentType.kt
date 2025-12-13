package github.kasuminova.prototypemachinery.api.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation

public interface RecipeProcessComponentType<C : RecipeProcessComponent> {

    /** Unique ID for this process component type / 类型唯一 ID */
    public val id: ResourceLocation

    /**
     * System that updates this component.
     * Returns null if the component does not require tick processing.
     * 
     * 处理该组件的系统。
     * 如果组件不需要 tick 处理，则返回 null。
     */
    public val system: RecipeProcessSystem<C>?

    /** Factory for creating component instances / 创建组件实例的工厂 */
    public fun createComponent(process: RecipeProcess): C

}