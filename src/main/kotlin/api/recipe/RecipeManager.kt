package github.kasuminova.prototypemachinery.api.recipe

import net.minecraft.util.ResourceLocation

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
 * - [github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess] - Executes recipes
 * - [github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent] - Requirement payloads grouped by type inside recipes
 */
public interface RecipeManager {

    /** Get a recipe by ID / 通过 ID 获取配方 */
    public fun get(id: String): MachineRecipe?

    /** Get all registered recipes / 获取所有已注册的配方 */
    public fun getAll(): Collection<MachineRecipe>

    /** Register a new recipe / 注册新配方 */
    public fun register(recipe: MachineRecipe)

    /**
     * Get recipes by a recipe group.
     *
     * 通过配方组获取配方。
     *
     * Default implementation falls back to filtering [getAll]. Implementations may override
     * with an index to avoid O(R) scans.
     */
    public fun getByGroup(groupId: ResourceLocation): Collection<MachineRecipe> {
        return getAll().filter { it.recipeGroups.contains(groupId) }
    }

    /**
     * Get recipes by any of the provided groups.
     *
     * 通过多个配方组获取配方（并集）。
     */
    public fun getByGroups(groupIds: Set<ResourceLocation>): Collection<MachineRecipe> {
        if (groupIds.isEmpty()) return emptyList()
        return getAll().filter { recipe -> recipe.recipeGroups.any(groupIds::contains) }
    }
}
