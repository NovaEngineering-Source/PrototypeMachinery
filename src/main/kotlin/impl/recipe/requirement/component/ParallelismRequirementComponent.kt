package github.kasuminova.prototypemachinery.impl.recipe.requirement.component

import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent

/**
 * Convenience requirement that adjusts [StandardMachineAttributes.PROCESS_PARALLELISM] for the running process.
 *
 * 便捷需求：修改运行中进程的 PROCESS_PARALLELISM 属性（默认 base=1）。
 *
 * 注意：当前仅提供“能把并行度写入 process.attributeMap”这块基础能力，
 * 真正的并行执行/配方逻辑会在后续修饰符系统与执行器完善后接入。
 */
public data class ParallelismRequirementComponent(
    /** Stable id within the recipe. / 配方内稳定 id */
    public val id: String,

    /** Target parallelism (>= 1). / 目标并行度（>=1） */
    public val parallelism: Long,

    /** Apply at start(). / 是否在 start() 阶段施加 */
    public val applyAtStart: Boolean = true,

    /** Remove at onEnd(). / 是否在 onEnd() 阶段撤销 */
    public val removeAtEnd: Boolean = true,
) : RecipeRequirementComponent {

    override val type: RecipeRequirementType<*> = RecipeRequirementTypes.PARALLELISM
}
