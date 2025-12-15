package github.kasuminova.prototypemachinery.api.recipe

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import net.minecraft.util.ResourceLocation

/**
 * # MachineRecipe - Recipe Definition
 * # MachineRecipe - 配方定义
 * 
 * Defines a recipe that can be executed by a machine. Contains requirements (inputs/outputs)
 * grouped by requirement type for efficient lookup and processing.
 * 
 * 定义可由机械执行的配方。包含按需求类型分组的需求（输入/输出），以便高效查找和处理。
 * 
 * ## Requirement Grouping / 需求分组
 * 
 * Requirements are grouped by type (e.g., ITEM, FLUID, ENERGY) for:
 * - Fast lookup during recipe matching
 * - Parallel processing by different systems
 * - Clear separation of concerns
 * 
 * 需求按类型分组（例如：ITEM、FLUID、ENERGY）用于:
 * - 配方匹配期间的快速查找
 * - 不同系统的并行处理
 * - 清晰的关注点分离
 * 
 * ## Related Classes / 相关类
 * 
 * - [RecipeRequirementComponent] - Individual requirement payload (input/output)
 * - [RecipeRequirementType] - Type of requirement (item/fluid/energy)
 * - [RecipeProcess] - Runtime execution context
 * - [RecipeManager] - Recipe registry and matching
 * 
 * @see RecipeRequirementComponent
 * @see RecipeRequirementType
 */
public interface MachineRecipe {

    /**
     * Unique identifier for this recipe.
     * 
     * 此配方的唯一标识符。
     */
    public val id: String

    /**
     * Recipe duration in ticks.
     * 配方持续时间（tick）。
     *
     * This is a minimal built-in field used by the default processor to decide when a process completes.
     * 默认配方执行器用于判断进程何时完成的最小内建字段。
     */
    public val durationTicks: Int
        get() = 1

    /**
     * Map of requirements grouped by type.
     * Each type can have multiple requirements (e.g., multiple item inputs).
     * 
     * 按类型分组的需求映射。
     * 每种类型可以有多个需求（例如，多个物品输入）。
     */
    public val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>>

    /**
     * Recipe groups that this recipe belongs to.
     *
     * 配方所属的配方组集合。
     *
     * This is intentionally a *set* to support recipes that can run in multiple contexts
     * (e.g. shared recipes across multiple machine families).
     *
     * 默认为空以保持对旧实现的兼容性。
     */
    public val recipeGroups: Set<ResourceLocation>
        get() = emptySet()

}