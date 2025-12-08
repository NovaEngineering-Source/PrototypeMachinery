package github.kasuminova.prototypemachinery.api.machine.recipe

/**
 * # RecipeManager - Recipe Registry and Lookup
 * # RecipeManager - 配方注册与查找
 *
 * Central registry for machine recipes. Provides lookup by ID, iteration over all recipes,
 * and registration utilities.
 *
 * 机械配方的中央注册表。提供按 ID 查找、遍历全部配方以及注册工具。
 *
 * ## Related Classes / 相关类
 * - [MachineRecipe] - Recipe definitions stored in this manager
 * - [github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess] - Executes recipes
 * - [github.kasuminova.prototypemachinery.api.machine.recipe.requirement.RecipeRequirement] - Requirements inside recipes
 */
public interface RecipeManager {

    /** Get a recipe by ID / 通过 ID 获取配方 */
    public fun get(id: String): MachineRecipe?

    /** Get all registered recipes / 获取所有已注册的配方 */
    public fun getAll(): Collection<MachineRecipe>

    /** Register a new recipe / 注册新配方 */
    public fun register(recipe: MachineRecipe)
}
