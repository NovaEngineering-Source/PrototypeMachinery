package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeExecutor
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess

/**
 * # FactoryRecipeProcessorComponent - Recipe process host component
 * # FactoryRecipeProcessorComponent - 配方进程承载组件
 *
 * A machine component that owns and ticks multiple [RecipeProcess] instances.
 * It typically enforces a concurrency limit and delegates ticking to registered [RecipeExecutor]s.
 *
 * 用于承载并驱动多个 [RecipeProcess] 的机器组件。
 * 通常会提供并发上限，并把 tick 逻辑委托给已注册的 [RecipeExecutor]。
 */
public interface FactoryRecipeProcessorComponent : MachineComponent {

    public val activeProcesses: MutableCollection<RecipeProcess>

    public val maxConcurrentProcesses: Int

    public val executors: MutableList<RecipeExecutor>

    /**
     * Attempt to start the given process.
     * Returns false if capacity/concurrency constraints prevent starting.
     *
     * 尝试启动指定进程。
     * 若容量/并发限制导致无法启动，返回 false。
     */
    public fun startProcess(process: RecipeProcess): Boolean

    /** Stop and remove a process. / 停止并移除一个进程。 */
    public fun stopProcess(process: RecipeProcess)

    /** Tick all active processes once. / 对所有活动进程进行一次 tick。 */
    public fun tickProcesses()

}
