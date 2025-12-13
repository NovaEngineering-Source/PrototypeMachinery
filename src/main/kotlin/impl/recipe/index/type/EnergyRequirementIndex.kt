package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.MachineInstanceImpl

public class EnergyRequirementIndex(
    // Map of Recipe -> Required Energy (per tick or total start cost)
    private val energyRequirements: Map<MachineRecipe, Long>
) : RequirementIndex {

    public override fun lookup(machine: MachineInstanceImpl): Set<MachineRecipe>? {
        // Pseudo-implementation

        // 1. Get current energy stored in machine
        // val energyStored = machine.energyComponent.stored
        val energyStored = 1000L // Mock

        // 2. Filter recipes
        val potentialRecipes = mutableSetOf<MachineRecipe>()
        for ((recipe, required) in energyRequirements) {
            if (energyStored >= required) {
                potentialRecipes.add(recipe)
            }
        }

        return if (potentialRecipes.isEmpty()) emptySet() else potentialRecipes
    }

    public companion object Factory : RequirementIndexFactory {
        override val requirementType: RecipeRequirementType<*> = RecipeRequirementTypes.ENERGY

        public override fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex? {
            // Check if machine uses energy

            val reqMap = mutableMapOf<MachineRecipe, Long>()

            for (recipe in recipes) {
                // val energyReq = recipe.requirements.find { it is EnergyRequirement }
                // if (energyReq != null) {
                //     reqMap[recipe] = energyReq.amount
                // }
            }

            if (reqMap.isEmpty()) return null

            return EnergyRequirementIndex(reqMap)
        }
    }
}
