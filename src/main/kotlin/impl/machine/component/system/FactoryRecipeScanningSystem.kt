package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.RecipeManager

public class FactoryRecipeScanningSystem(
    private val recipeManager: RecipeManager
) : MachineSystem<FactoryRecipeProcessorComponent> {

    override fun onPreTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {}

    override fun onTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {
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

    override fun onPostTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {}

}
