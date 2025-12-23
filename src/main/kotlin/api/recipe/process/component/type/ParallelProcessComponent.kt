package github.kasuminova.prototypemachinery.api.recipe.process.component.type

import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent

/**
 * # ParallelProcessComponent - Effective parallelism marker
 * # ParallelProcessComponent - 有效并行度标记组件
 *
 * Holds the effective parallelism value for a process.
 * Consumers typically treat it as >= 1 (fallback to 1 if absent).
 *
 * 存储进程的有效并行度。
 * 使用方通常将其视为 >= 1（若不存在则回退为 1）。
 */
public interface ParallelProcessComponent : RecipeProcessComponent {

    public val parallelism: Long

}