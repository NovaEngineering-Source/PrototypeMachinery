package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.component.container.EnumerableItemKeyContainer
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureItemKeyContainer
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.DynamicItemInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.api.util.PortMode
import net.minecraft.item.ItemStack

public class ItemRequirementIndex(
    private val index: Map<PMKey<ItemStack>, Set<MachineRecipe>>,
    private val dynamicRecipes: Set<MachineRecipe>
) : RequirementIndex {

    public override fun lookup(machine: MachineInstance): Set<MachineRecipe>? {
        // Use the key-level API for scanning (see StructureKeyContainers docs).
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureItemKeyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.OUTPUT) }

        if (sources.isEmpty()) {
            // No item sources, no opinion on recipes.
            return null
        }

        var hasEnumerableSource = false
        var hasAnyKey = false
        val potentialRecipes = LinkedHashSet<MachineRecipe>()

        for (container in sources) {
            val enumerable = container as? EnumerableItemKeyContainer ?: continue
            hasEnumerableSource = true

            for (key in enumerable.getAllKeysSnapshot()) {
                hasAnyKey = true
                index[key]?.let { potentialRecipes.addAll(it) }
            }
        }

        // Dynamic inputs depend on runtime enumeration/matching, so they must not be excluded by a key index.
        // If we have any enumerable sources, keep dynamic recipes as candidates (conservative).
        if (hasEnumerableSource && dynamicRecipes.isNotEmpty()) {
            potentialRecipes.addAll(dynamicRecipes)
        }

        // If we cannot enumerate keys for any source container, indexing can't contribute.
        if (!hasEnumerableSource) return null

        // We enumerated successfully and found no keys => hard empty.
        if (!hasAnyKey) return emptySet()

        return if (potentialRecipes.isEmpty()) emptySet() else potentialRecipes
    }

    public companion object Factory : RequirementIndexFactory {
        override val requirementType: RecipeRequirementType<*> = RecipeRequirementTypes.ITEM

        public override fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex? {
            val map = mutableMapOf<PMKey<ItemStack>, MutableSet<MachineRecipe>>()
            val dynamic = LinkedHashSet<MachineRecipe>()

            for (recipe in recipes) {
                // Get all ItemRequirementComponents from this recipe
                val itemReqs = recipe.requirements[RecipeRequirementTypes.ITEM]
                if (itemReqs.isNullOrEmpty()) continue

                for (req in itemReqs) {
                    if (req is github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent) {
                        // Index fuzzy candidates conservatively (they are explicit and thus safe to index).
                        @Suppress("UNCHECKED_CAST")
                        val fuzzy = req.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<ItemStack>>
                        if (!fuzzy.isNullOrEmpty()) {
                            for (group in fuzzy) {
                                for (cand in group.candidates) {
                                    map.computeIfAbsent(cand) { mutableSetOf() }.add(recipe)
                                }
                            }
                        }

                        // Dynamic item inputs cannot be indexed by concrete keys (matcher decides at runtime).
                        val dyn = req.properties[RequirementPropertyKeys.DYNAMIC_ITEM_INPUTS] as? List<DynamicItemInputGroup>
                        if (!dyn.isNullOrEmpty()) {
                            dynamic += recipe
                        }

                        // Index each input item type
                        for (inputKey in req.inputs) {
                            // The key is already a PMKey<ItemStack>
                            map.computeIfAbsent(inputKey) { mutableSetOf() }.add(recipe)
                        }
                    }
                }
            }

            if (map.isEmpty() && dynamic.isEmpty()) return null

            return ItemRequirementIndex(map, dynamic)
        }
    }
}
