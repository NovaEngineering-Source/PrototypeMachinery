package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.RecipeManager
import github.kasuminova.prototypemachinery.api.recipe.index.IRecipeIndexRegistry
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.scanning.RecipeParallelismConstraintRegistry
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.OverlayMachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent
import net.minecraft.util.ResourceLocation
import kotlin.math.floor

public class FactoryRecipeScanningSystem(
    private val recipeManager: RecipeManager
) : MachineSystem<FactoryRecipeProcessorComponent> {

    private companion object {
        private val warnedTypes: MutableSet<ResourceLocation> = HashSet()
    }

    override fun onPreTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {}

    override fun onTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {
        if (!machine.isFormed()) return
        if (component.activeProcesses.size >= component.maxConcurrentProcesses) return

        val alreadyRunningIds: Set<String> = component.activeProcesses
            .asSequence()
            .map { it.recipe.id }
            .toSet()

        val groups = machine.type.recipeGroups
        if (groups.isEmpty()) {
            // No recipe groups configured for this machine type.
            // Avoid scanning the global recipe list as it scales poorly and usually indicates WIP machine definitions.
            if (warnedTypes.add(machine.type.id)) {
                PrototypeMachinery.logger.warnWithBlockEntity(
                    "Machine type `${machine.type.id}` has empty recipeGroups; scanning is disabled (set MachineType.recipeGroups to enable recipes).",
                    machine.blockEntity
                )
            }
            return
        }

        // Try to use recipe index for filtering if available
        var candidates: Collection<MachineRecipe>

        val index = IRecipeIndexRegistry.INSTANCE.getIndex(machine.type)
        if (index != null) {
            // Index is available: use it to filter recipes first.
            // If the index has no opinion (e.g. machine has no enumerable ports for indexed types), fall back
            // to the group-limited scan for correctness.
            val indexedCandidates = index.lookupOrNull(machine)
            candidates = indexedCandidates ?: recipeManager.getByGroups(groups)
        } else {
            // No index for this machine type, use full recipe scan
            candidates = recipeManager.getByGroups(groups)
        }

        // Iterate through candidate recipes
        for (recipe in candidates) {
            if (component.activeProcesses.size >= component.maxConcurrentProcesses) break

            // Minimal de-dupe: avoid spamming the same recipe every tick.
            if (alreadyRunningIds.contains(recipe.id)) continue

            val process = RecipeProcessImpl(machine, recipe)

            val limit = parallelLimit(machine, recipe)
            val parallels = computeMaxParallelsByConstraints(machine, recipe, limit)
            if (parallels <= 0) {
                // Cannot satisfy even 1x inputs; skip this recipe.
                continue
            }

            // Store effective parallelism for this process instance.
            // Requirement systems will scale amounts by this value.
            setProcessParallelism(process, parallels)

            component.startProcess(process)

            // Small policy: start at most one new process per tick.
            // This prevents O(R) process churn on large recipe sets.
            break

            // Placeholder for recipe matching logic.
            // In a full implementation, we would check if the machine's inventory/state matches the recipe's requirements.
            // if (matches(machine, recipe)) {
            //     val process = RecipeProcessImpl(machine, recipe)
            //     component.startProcess(process)
            // }
        }
    }

    override fun onPostTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {}

    private fun parallelLimit(machine: MachineInstance, recipe: MachineRecipe): Int {
        val machineLimitRaw = machine.attributeMap.attributes[StandardMachineAttributes.PROCESS_PARALLELISM]?.value ?: 1.0
        val machineLimit = floor(machineLimitRaw).toInt().coerceAtLeast(1)

        val recipeCap = recipe.requirements[RecipeRequirementTypes.PARALLELISM]
            ?.filterIsInstance<ParallelismRequirementComponent>()
            ?.minOfOrNull { it.parallelism.coerceAtLeast(1L) }
            ?.coerceAtMost(Int.MAX_VALUE.toLong())
            ?.toInt()

        return if (recipeCap != null) minOf(machineLimit, recipeCap) else machineLimit
    }

    /**
     * Compute maximum parallels using registered scan-time constraints.
     *
     * - Built-in constraints (ITEM/FLUID/ENERGY) check both inputs and outputs.
     * - If any requirement type lacks a registered constraint, we conservatively clamp the search limit to 1.
     */
    private fun computeMaxParallelsByConstraints(machine: MachineInstance, recipe: MachineRecipe, limit: Int): Int {
        if (limit <= 0) return 0

        var hi = limit
        var hasUnknown = false

        // Optional: allow constraints to provide upper bounds and detect unknown requirement types.
        for ((type, comps) in recipe.requirements) {
            if (type == RecipeRequirementTypes.PARALLELISM) continue
            if (comps.isEmpty()) continue

            val constraint = RecipeParallelismConstraintRegistry.get(type.id)
            if (constraint == null) {
                hasUnknown = true
                continue
            }

            hi = minOf(hi, constraint.upperBound(machine, recipe, comps, hi).coerceAtLeast(0))
        }

        if (hasUnknown) {
            // Unknown requirement types may scale with k; without constraints we can only safely run at k=1.
            hi = minOf(hi, 1)
        }

        if (hi <= 0) return 0

        if (hi == 1) {
            return if (canSatisfyAllConstraints(machine, recipe, 1)) 1 else 0
        }

        var lo = 1
        var best = 0

        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (canSatisfyAllConstraints(machine, recipe, mid)) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }

        return best
    }

    private fun canSatisfyAllConstraints(machine: MachineInstance, recipe: MachineRecipe, parallels: Int): Boolean {
        if (parallels <= 0) return false
        for ((type, comps) in recipe.requirements) {
            if (type == RecipeRequirementTypes.PARALLELISM) continue
            if (comps.isEmpty()) continue

            val constraint = RecipeParallelismConstraintRegistry.get(type.id) ?: continue
            if (!constraint.canSatisfy(machine, recipe, comps, parallels)) return false
        }
        return true
    }

    private fun setProcessParallelism(process: RecipeProcessImpl, parallels: Int) {
        val overlay = process.attributeMap as? OverlayMachineAttributeMapImpl ?: return
        val instance = overlay.getOrCreateAttribute(StandardMachineAttributes.PROCESS_PARALLELISM, defaultBase = 1.0)
        instance.base = parallels.coerceAtLeast(1).toDouble()
    }

}
