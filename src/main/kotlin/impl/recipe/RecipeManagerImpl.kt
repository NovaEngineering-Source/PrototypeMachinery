package github.kasuminova.prototypemachinery.impl.recipe

import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.RecipeManager
import net.minecraft.util.ResourceLocation

// TODO Replace with registry.
public object RecipeManagerImpl : RecipeManager {
    private val recipes: MutableMap<String, MachineRecipe> = mutableMapOf()
    private val recipesByGroup: MutableMap<ResourceLocation, MutableSet<MachineRecipe>> = mutableMapOf()

    override fun get(id: String): MachineRecipe? = recipes[id]
    override fun getAll(): Collection<MachineRecipe> = recipes.values
    override fun register(recipe: MachineRecipe) {
        recipes[recipe.id] = recipe

        // Update group index.
        // We do not attempt to remove stale entries on overwrite, as recipes are expected to be registered once.
        // If overwrite happens, the group index may contain both; tests can clear/restore.
        for (group in recipe.recipeGroups) {
            recipesByGroup.computeIfAbsent(group) { linkedSetOf() }.add(recipe)
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
