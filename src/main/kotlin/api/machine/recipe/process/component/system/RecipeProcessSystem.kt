package github.kasuminova.prototypemachinery.api.machine.recipe.process.component.system

import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.system.ComponentSystem

/**
 * System that processes RecipeProcessComponents each tick (pre/tick/post).
 * 按 tick 处理进程组件的系统（前/中/后）。
 */
public interface RecipeProcessSystem<C : RecipeProcessComponent> : ComponentSystem<RecipeProcess, C> {

}