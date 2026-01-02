package github.kasuminova.prototypemachinery.impl.recipe

import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.RecipeManager
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory recipe registry implementation.
 *
 * 这是一个轻量的内存“注册表”实现：支持按 id 查询、按 group 查询，以及注册时维护 group 索引。
 */
public object RecipeManagerImpl : RecipeManager {
    private val recipes: MutableMap<String, MachineRecipe> = ConcurrentHashMap()
    private val recipesByGroup: MutableMap<ResourceLocation, MutableSet<MachineRecipe>> = ConcurrentHashMap()

    override fun get(id: String): MachineRecipe? = recipes[id]
    override fun getAll(): Collection<MachineRecipe> = recipes.values
    override fun register(recipe: MachineRecipe) {
        val previous = recipes.put(recipe.id, recipe)

        // Update group index.
        // Recipes are expected to be registered once during init. If overwrite happens, we try to remove the
        // previous recipe from its old groups as a best-effort cleanup.
        if (previous != null && previous !== recipe) {
            for (g in previous.recipeGroups) {
                recipesByGroup[g]?.remove(previous)
            }
        }
        for (group in recipe.recipeGroups) {
            val set = recipesByGroup.computeIfAbsent(group) { ConcurrentHashMap.newKeySet() }
            set.add(recipe)
        }
    }

    override fun getByGroup(groupId: ResourceLocation): Collection<MachineRecipe> {
        return recipesByGroup[groupId]?.toList() ?: emptyList()
    }

    override fun getByGroups(groupIds: Set<ResourceLocation>): Collection<MachineRecipe> {
        if (groupIds.isEmpty()) return emptyList()
        val out = LinkedHashSet<MachineRecipe>()
        for (g in groupIds) {
            recipesByGroup[g]?.let(out::addAll)
        }
        return out.toList()
    }

    /**
     * Test-only helpers (same module).
     *
     * We keep this internal to avoid leaking mutation APIs to consumers.
     */
    @JvmSynthetic
    internal fun snapshotForTests(): Snapshot = Snapshot(
        recipes = recipes.toMap(),
        byGroup = recipesByGroup.mapValues { (_, v) -> v.toList() }
    )

    @JvmSynthetic
    internal fun restoreForTests(snapshot: Snapshot) {
        recipes.clear()
        recipes.putAll(snapshot.recipes)

        recipesByGroup.clear()
        for ((group, list) in snapshot.byGroup) {
            recipesByGroup[group] = LinkedHashSet(list)
        }
    }

    @JvmSynthetic
    internal fun clearForTests() {
        recipes.clear()
        recipesByGroup.clear()
    }

    internal data class Snapshot(
        val recipes: Map<String, MachineRecipe>,
        val byGroup: Map<ResourceLocation, List<MachineRecipe>>,
    )
}
