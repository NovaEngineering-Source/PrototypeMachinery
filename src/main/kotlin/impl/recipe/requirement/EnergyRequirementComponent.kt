package github.kasuminova.prototypemachinery.impl.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.IdentifiedRecipeRequirementComponent

/**
 * Energy requirement payload.
 *
 * 能量需求组件通常由脚本/数据驱动层创建并绑定到配方：
 * - [input]：start 阶段需要消耗的能量
 * - [output]：onEnd 阶段需要产出的能量
 *
 * ## Per-tick（可选）
 * 若配方需要在运行期间每 tick 扣/产能量，可使用固有字段：
 * - [inputPerTick]
 * - [outputPerTick]
 *
 * 当 [outputPerTick] > 0 且输出端口已满时：
 * - 默认返回 Blocked
 * - 若 `ignore_output_full = true`，则允许部分注入（或 0 注入）并继续。
 */
public data class EnergyRequirementComponent(
    /** Stable id within the recipe. / 配方内稳定 id */
    override val id: String,

    /** Energy to consume at start(). / start 阶段需要消耗的能量 */
    public val input: Long = 0L,

    /** Energy to output at onEnd(). / onEnd 阶段需要产出的能量 */
    public val output: Long = 0L,

    /** Energy to consume each tick. / 每 tick 需要消耗的能量 */
    public val inputPerTick: Long = 0L,

    /** Energy to output each tick. / 每 tick 需要产出的能量 */
    public val outputPerTick: Long = 0L,

    override val properties: Map<String, Any> = emptyMap(),
) : IdentifiedRecipeRequirementComponent {

    override val type: RecipeRequirementType<*>
        get() = RecipeRequirementTypes.ENERGY

}