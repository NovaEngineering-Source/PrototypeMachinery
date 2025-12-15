package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey

public class ItemRequirementIndex(
    private val index: Map<PMItemKey, Set<MachineRecipe>>
) : RequirementIndex {

    public override fun lookup(machine: MachineInstance): Set<MachineRecipe>? {
        // Pseudo-implementation as per prompt

        // 1. Get all ItemContainerComponents from the machine
        // val itemComponents = machine.componentMap.getByInstanceOf(ItemContainerComponent::class.java)

        // 2. Collect all available items as ItemStackKeys
        // val availableKeys = mutableSetOf<ItemStackKey>()
        // for (component in itemComponents) {
        //     for (stack in component.items) {
        //         if (!stack.isEmpty) {
        //             availableKeys.add(ItemStackKeyType.create(stack) as ItemStackKey)
        //         }
        //     }
        // }

        // 3. Query the index
        val potentialRecipes = mutableSetOf<MachineRecipe>()

        // In a real implementation, we would iterate availableKeys and addAll(index[key])
        // However, recipes usually require MULTIPLE inputs.
        // If we just union all recipes that match ANY input, we get a superset.
        // If we intersect, we might miss recipes if we don't have ALL inputs yet?
        // The prompt says: "RecipeIndex.lookup... to get a set of potential recipes that can be crafted"
        // Usually, an index returns recipes that match *at least one* input, 
        // and the full check verifies the rest.
        // OR, if we have multiple inputs, we can be smarter.
        // But for a simple index, "Union of recipes for all available items" is a safe "Potential" set.
        // Wait, if I have Iron, and recipe needs Iron + Gold.
        // Index(Iron) -> [RecipeA]
        // Index(Gold) -> [RecipeA]
        // If I only have Iron, Index returns [RecipeA].
        // Then the full check fails because Gold is missing. This is correct.

        // Mock logic for compilation
        /*
        for (key in availableKeys) {
            val recipes = index[key]
            if (recipes != null) {
                potentialRecipes.addAll(recipes)
            }
        }
        */

        return if (potentialRecipes.isEmpty()) null else potentialRecipes
    }

    public companion object Factory : RequirementIndexFactory {
        override val requirementType: RecipeRequirementType<*> = RecipeRequirementTypes.ITEM

        public override fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex? {
            // 1. Check if machine has ItemRequirementComponents and if they are static
            // if (!areItemComponentsStatic(machineType)) return null

            val map = mutableMapOf<PMItemKey, MutableSet<MachineRecipe>>()

            for (recipe in recipes) {
                // Extract item inputs from recipe
                // val itemRequirements = recipe.requirements.filterIsInstance<ItemRequirement>()
                // for (req in itemRequirements) {
                //     for (stack in req.inputs) {
                //          val key = ItemStackKeyType.create(stack) as ItemStackKey
                //          map.computeIfAbsent(key) { mutableSetOf() }.add(recipe)
                //     }
                // }
            }

            if (map.isEmpty()) return null

            return ItemRequirementIndex(map)
        }
    }
}
