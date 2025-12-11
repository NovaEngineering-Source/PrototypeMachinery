package github.kasuminova.prototypemachinery.api.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem

/**
 * # RecipeRequirement - Single Requirement Descriptor
 * # RecipeRequirement - 单个需求描述符
 *
 * Represents a single input/output requirement for a recipe (item, fluid, energy, etc.).
 * Contains type, data payload, and priority used during matching and execution.
 *
 * 表示配方的单个输入/输出需求（物品、流体、能量等）。
 * 包含类型、数据负载和用于匹配与执行的优先级。
 *
 * ## Priority / 优先级
 * Higher priority requirements are evaluated first to fail fast on cheap checks
 * (e.g., energy availability before item checks).
 *
 * 更高优先级的需求会优先评估，以在廉价检查上快速失败（如先检查能量再检查物品）。
 *
 * ## Related Classes / 相关类
 * - [RecipeRequirementType] - Declares the system used to process this requirement
 * - [RecipeRequirementData] - Payload describing amounts, filters, etc.
 * - [RecipeRequirementSystem] - Executes requirements
 */
public interface RecipeRequirement {

    /** Unique identifier for this requirement / 此需求的唯一标识符 */
    public val id: String

    /** Type of this requirement, determines handling system / 需求类型，决定处理系统 */
    public val type: RecipeRequirementType<*>

    /** Data payload (amounts, filters, sides) / 数据负载（数量、过滤器、方向） */
    public val data: RecipeRequirementData

    /**
     * Priority for evaluation (higher checked first).
     * 评估优先级（越高越先检查）。
     */
    public val priority: Int get() = 0

    /**
     * List of modifiers to apply to this requirement.
     * 应用于此需求的修改器列表。
     *
     * Modifiers are applied in order before the requirement is executed.
     * 修改器在需求执行前按顺序应用。
     *
     * TODO: Add ZenScript API to allow users to add modifiers.
     */
    public val modifiers: List<RecipeRequirementModifier<*>> get() = emptyList()

}