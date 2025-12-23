package github.kasuminova.prototypemachinery.api.recipe.process

import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent

/**
 * # RecipeExecutor - Process ticking strategy
 * # RecipeExecutor - 进程 tick 执行器
 *
 * Ticks one or more recipe processes owned by a [FactoryRecipeProcessorComponent].
 * Implementations usually iterate processes, advance state, and apply requirement systems.
 *
 * 用于驱动 [FactoryRecipeProcessorComponent] 中的配方进程 tick。
 * 实现通常会遍历进程、推进状态，并调用需求系统。
 */
public interface RecipeExecutor {

    public fun tick(processor: FactoryRecipeProcessorComponent)

}
