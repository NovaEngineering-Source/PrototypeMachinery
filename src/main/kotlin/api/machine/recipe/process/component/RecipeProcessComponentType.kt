package github.kasuminova.prototypemachinery.api.machine.recipe.process.component

import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.system.RecipeProcessSystem
import net.minecraft.util.ResourceLocation

public interface RecipeProcessComponentType<C : RecipeProcessComponent> {

    /** Unique ID for this process component type / 类型唯一 ID */
    public val id: ResourceLocation

    /** System that updates this component / 处理该组件的系统 */
    public val system: RecipeProcessSystem<C>

    /** Factory for creating component instances / 创建组件实例的工厂 */
    public fun createComponent(process: RecipeProcess): C

}