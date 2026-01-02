package github.kasuminova.prototypemachinery.impl.recipe.requirement

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.IdentifiedRecipeRequirementComponent
import net.minecraft.item.ItemStack

/**
 * Item requirement payload.
 *
 * 物品需求组件通常由脚本/数据驱动层创建并绑定到配方：
 * - [inputs]：需要从机器侧“可输出到机器”的物品端口中消耗的物品
 * - [outputs]：需要向机器侧“可输入到端口”的物品端口中产出的物品
 * - [properties]：额外的自定义数据/标志（例如 ignore_output_full 等）
 *
 * 注意：输入/输出使用 [PMKey] 表达“类型键 + 数量（Long）”，以支持超大计数与稳定序列化。
 */
public data class ItemRequirementComponent(
    /** Stable id within the recipe. / 配方内稳定 id */
    override val id: String,

    /** Items to consume at start(). / start 阶段需要消耗的物品列表 */
    public val inputs: List<PMKey<ItemStack>> = emptyList(),

    /** Items to output at onEnd(). / onEnd 阶段需要产出的物品列表 */
    public val outputs: List<PMKey<ItemStack>> = emptyList(),

    override val properties: Map<String, Any> = emptyMap(),
) : IdentifiedRecipeRequirementComponent {

    override val type: RecipeRequirementType<*>
        get() = RecipeRequirementTypes.ITEM

}