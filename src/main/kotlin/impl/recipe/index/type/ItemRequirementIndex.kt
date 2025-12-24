package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.machine.component.container.ItemContainerComponent
import net.minecraft.item.ItemStack

public class ItemRequirementIndex(
    private val index: Map<PMKey<ItemStack>, Set<MachineRecipe>>
) : RequirementIndex {

    public override fun lookup(machine: MachineInstance): Set<MachineRecipe>? {
        // 1. Get all ItemContainerComponents from the machine
        val itemComponents = machine.componentMap.getByInstanceOf(ItemContainerComponent::class.java)

        if (itemComponents.isEmpty()) {
            // No item containers, no opinion on recipes
            return null
        }

        // 2. Collect all available items as PMKey<ItemStack>
        // We're only interested in the type (prototype), not the count
        // because the index is a conservative filter
        val availableKeys = mutableSetOf<PMKey<ItemStack>>()

        for (component in itemComponents) {
            for (slot in 0 until component.slots) {
                val stack = component.getItem(slot)
                if (!stack.isEmpty) {
                    // Create a PMKey from the ItemStack
                    // The count doesn't matter for equality, so we use 1
                    val key = github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType.create(stack)
                    availableKeys.add(key)
                }
            }
        }

        if (availableKeys.isEmpty()) {
            // No items available, no recipes can match
            return emptySet()
        }

        // 3. Query the index
        // Union of all recipes that match ANY available item
        // This is conservative: it may include recipes that need other inputs,
        // but won't exclude recipes that could potentially match
        val potentialRecipes = mutableSetOf<MachineRecipe>()

        for (key in availableKeys) {
            val recipes = index[key]
            if (recipes != null) {
                potentialRecipes.addAll(recipes)
            }
        }

        return if (potentialRecipes.isEmpty()) emptySet() else potentialRecipes
    }

    public companion object Factory : RequirementIndexFactory {
        override val requirementType: RecipeRequirementType<*> = RecipeRequirementTypes.ITEM

        public override fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex? {
            val map = mutableMapOf<PMKey<ItemStack>, MutableSet<MachineRecipe>>()

            for (recipe in recipes) {
                // Get all ItemRequirementComponents from this recipe
                val itemReqs = recipe.requirements[RecipeRequirementTypes.ITEM]
                if (itemReqs.isNullOrEmpty()) continue

                for (req in itemReqs) {
                    if (req is github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent) {
                        // Index each input item type
                        for (inputKey in req.inputs) {
                            // The key is already a PMKey<ItemStack>
                            map.computeIfAbsent(inputKey) { mutableSetOf() }.add(recipe)
                        }
                    }
                }
            }

            if (map.isEmpty()) return null

            return ItemRequirementIndex(map)
        }
    }
}
