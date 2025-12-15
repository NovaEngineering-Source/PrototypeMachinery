package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.recipe.RecipeManager
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import net.minecraft.util.ResourceLocation

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

        val candidates = recipeManager.getByGroups(groups)

        // Iterate through candidate recipes
        for (recipe in candidates) {
            if (component.activeProcesses.size >= component.maxConcurrentProcesses) break

            // Minimal de-dupe: avoid spamming the same recipe every tick.
            if (alreadyRunningIds.contains(recipe.id)) continue

            // NOTE: This is intentionally minimal: we do not attempt a full requirement match here.
            // The processor system will run transactional start/tick/end stages.
            val process = RecipeProcessImpl(machine, recipe)
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

}
