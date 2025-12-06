package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.type.EnergyContainerComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.TickableRecipeRequirementSystem

public open class EnergyRequirementSystem : TickableRecipeRequirementSystem<EnergyContainerComponent> {

    override fun check(machine: MachineInstance, component: EnergyContainerComponent, process: RecipeProcess): ProcessResult {
        // Check if energy is available
        return ProcessResult.Success
    }

    override fun onStart(machine: MachineInstance, component: EnergyContainerComponent, process: RecipeProcess): ProcessResult {
        // Energy might be consumed at start for activation
        return ProcessResult.Success
    }

    override fun onTick(machine: MachineInstance, component: EnergyContainerComponent, process: RecipeProcess): ProcessResult {
        // TODO: Implement continuous energy consumption
        // Iterate recipe.recipe.requirements, find ENERGY type requirements, and extract from component
        return ProcessResult.Success
    }

    override fun onEnd(machine: MachineInstance, component: EnergyContainerComponent, process: RecipeProcess): ProcessResult {
        // Energy usually not handled at end unless it's generation
        return ProcessResult.Success
    }

}
