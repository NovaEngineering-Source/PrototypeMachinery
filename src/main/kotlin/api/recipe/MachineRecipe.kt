package github.kasuminova.prototypemachinery.api.recipe

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirement
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType

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
 * - [RecipeRequirement] - Individual requirement (input/output)
 * - [RecipeRequirementType] - Type of requirement (item/fluid/energy)
 * - [RecipeProcess] - Runtime execution context
 * - [RecipeManager] - Recipe registry and matching
 * 
 * @see RecipeRequirement
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
     * Map of requirements grouped by type.
     * Each type can have multiple requirements (e.g., multiple item inputs).
     * 
     * 按类型分组的需求映射。
     * 每种类型可以有多个需求（例如，多个物品输入）。
     */
    public val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirement>>

}