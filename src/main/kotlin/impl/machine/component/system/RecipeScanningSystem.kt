package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.RecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.RecipeManager

public class RecipeScanningSystem(
    private val recipeManager: RecipeManager
) : MachineSystem<RecipeProcessorComponent> {

    override fun onPreTick(machine: MachineInstance, component: RecipeProcessorComponent) {}

    override fun onTick(machine: MachineInstance, component: RecipeProcessorComponent) {
        if (component.activeProcesses.size >= component.maxConcurrentProcesses) return

        // Iterate through all registered recipes
        for (recipe in recipeManager.getAll()) {
            if (component.activeProcesses.size >= component.maxConcurrentProcesses) break

            // Placeholder for recipe matching logic.
            // In a full implementation, we would check if the machine's inventory/state matches the recipe's requirements.
            // if (matches(machine, recipe)) {
            //     val process = RecipeProcessImpl(machine, recipe)
            //     component.startProcess(process)
            // }
        }
    }

    override fun onPostTick(machine: MachineInstance, component: RecipeProcessorComponent) {}

}
