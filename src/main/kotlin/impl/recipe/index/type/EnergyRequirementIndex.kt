package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.machine.component.container.EnergyContainerComponent
import java.util.Arrays

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
    // Sorted by required energy ascending.
    private val requiredEnergies: LongArray,
    private val recipesByRequired: Array<MachineRecipe>
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

        if (requiredEnergies.isEmpty()) return null

        // 3. Find recipes with required <= totalEnergyStored using binary search.
        val idx = Arrays.binarySearch(requiredEnergies, totalEnergyStored)
        val endExclusive = if (idx >= 0) idx + 1 else -idx - 1

        if (endExclusive <= 0) return emptySet()

        val out = LinkedHashSet<MachineRecipe>(endExclusive)
        for (i in 0 until endExclusive) {
            out += recipesByRequired[i]
        }

        return out
    }

    public companion object Factory : RequirementIndexFactory {
        override val requirementType: RecipeRequirementType<*> = RecipeRequirementTypes.ENERGY

        public override fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex? {
            data class Entry(val required: Long, val recipe: MachineRecipe)
            val entries = ArrayList<Entry>()

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
                    entries += Entry(totalEnergyRequired, recipe)
                }
            }

            if (entries.isEmpty()) return null

            entries.sortBy { it.required }

            val required = LongArray(entries.size)
            val byRequired = arrayOfNulls<MachineRecipe>(entries.size)
            for (i in entries.indices) {
                required[i] = entries[i].required
                byRequired[i] = entries[i].recipe
            }

            @Suppress("UNCHECKED_CAST")
            return EnergyRequirementIndex(required, byRequired as Array<MachineRecipe>)
        }
    }
}
