package github.kasuminova.prototypemachinery.impl.recipe.requirement

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.IdentifiedRecipeRequirementComponent
import net.minecraftforge.fluids.FluidStack

/**
 * Fluid requirement payload.
 *
 * 流体需求组件通常由脚本/数据驱动层创建并绑定到配方：
 * - [inputs]：需要消耗的流体
 * - [outputs]：需要产出的流体
 * - [inputsPerTick]：运行期间每 tick 需要消耗的流体（可选）
 * - [outputsPerTick]：运行期间每 tick 需要产出的流体（可选）
 * - [properties]：额外自定义数据/标志位
 *
 * 注意：以上列表使用 [PMKey] 表达“类型键 + 数量（Long）”，以支持超大计数与稳定序列化。
 *
 * 当 [outputsPerTick] 非空且输出端口已满时：
 * - 默认返回 Blocked
 * - 若 `ignore_output_full = true`，则允许部分注入（或 0 注入）并继续。
 */
public data class FluidRequirementComponent(
    /** Stable id within the recipe. / 配方内稳定 id */
    override val id: String,

    /** Fluids to consume at start(). / start 阶段需要消耗的流体列表 */
    public val inputs: List<PMKey<FluidStack>> = emptyList(),

    /** Fluids to output at onEnd(). / onEnd 阶段需要产出的流体列表 */
    public val outputs: List<PMKey<FluidStack>> = emptyList(),

    /** Fluids to consume per tick. / 每 tick 需要消耗的流体列表 */
    public val inputsPerTick: List<PMKey<FluidStack>> = emptyList(),

    /** Fluids to output per tick. / 每 tick 需要产出的流体列表 */
    public val outputsPerTick: List<PMKey<FluidStack>> = emptyList(),

    override val properties: Map<String, Any> = emptyMap(),
) : IdentifiedRecipeRequirementComponent {

    override val type: RecipeRequirementType<*>
        get() = RecipeRequirementTypes.FLUID

}