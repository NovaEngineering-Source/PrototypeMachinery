package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.machine.component.container.EnergyContainerComponent

/**
 * # EnergyRequirementIndex
 * # 能量需求索引
 *
 * Index for energy-based recipe requirements.
 * Filters recipes based on the machine's current stored energy.
 *
 * 基于能量的配方需求索引。
 * 根据机器当前存储的能量过滤配方。
 */
public class EnergyRequirementIndex(
    // Map of Recipe -> Required Energy (start cost + per-tick * duration)
    private val energyRequirements: Map<MachineRecipe, Long>
) : RequirementIndex {

    public override fun lookup(machine: MachineInstance): Set<MachineRecipe>? {
        // 1. Get all EnergyContainerComponents from the machine
        val energyComponents = machine.componentMap.getByInstanceOf(EnergyContainerComponent::class.java)

        if (energyComponents.isEmpty()) {
            // No energy containers, no opinion on recipes
            return null
        }

        // 2. Calculate total energy stored across all components
        var totalEnergyStored = 0L
        for (component in energyComponents) {
            totalEnergyStored += component.stored
        }

        // 3. Filter recipes by energy requirement
        val potentialRecipes = mutableSetOf<MachineRecipe>()
        for ((recipe, required) in energyRequirements) {
            if (totalEnergyStored >= required) {
                potentialRecipes.add(recipe)
            }
        }

        // If no recipes match, return empty set (hard filter)
        return if (potentialRecipes.isEmpty()) emptySet() else potentialRecipes
    }

    public companion object Factory : RequirementIndexFactory {
        override val requirementType: RecipeRequirementType<*> = RecipeRequirementTypes.ENERGY

        public override fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex? {
            val reqMap = mutableMapOf<MachineRecipe, Long>()

            for (recipe in recipes) {
                // Get all EnergyRequirementComponents from this recipe
                val energyReqs = recipe.requirements[RecipeRequirementTypes.ENERGY]
                if (energyReqs.isNullOrEmpty()) continue

                var totalEnergyRequired = 0L

                for (req in energyReqs) {
                    if (req is github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent) {
                        // Calculate total energy needed for this recipe
                        // Start cost + (per-tick cost * duration)
                        val startCost = req.input
                        val perTickCost = req.inputPerTick
                        val duration = recipe.durationTicks

                        val totalCost = startCost + (perTickCost * duration)
                        totalEnergyRequired += totalCost
                    }
                }

                if (totalEnergyRequired > 0) {
                    reqMap[recipe] = totalEnergyRequired
                }
            }

            if (reqMap.isEmpty()) return null

            return EnergyRequirementIndex(reqMap)
        }
    }
}
