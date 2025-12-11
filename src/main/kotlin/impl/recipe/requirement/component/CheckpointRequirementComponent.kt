package github.kasuminova.prototypemachinery.impl.recipe.requirement.component

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.CheckpointRequirements

/**
 * # CheckpointRequirementComponent
 * # 检查点需求组件
 *
 * A wrapper component that enforces another requirement at a specific time (tick) in the process.
 *
 * 一个包装组件，用于在进程的特定时间（tick）强制执行另一个需求。
 *
 * @param time The tick time at which the requirement should be checked. / 应检查需求的 tick 时间。
 * @param requirement The actual requirement to enforce. / 要强制执行的实际需求。
 * @param scaleWithProcess Whether the time should be scaled if the process duration is scaled. / 如果进程持续时间被缩放，时间是否也应缩放。
 */
public data class CheckpointRequirementComponent(
    val time: Int,
    val requirement: RecipeRequirementComponent,
    val scaleWithProcess: Boolean = true
) : RecipeRequirementComponent {

    override val type: RecipeRequirementType<*> = CheckpointRequirements.CHECKPOINT_TYPE

}
