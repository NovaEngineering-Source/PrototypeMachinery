package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeTransaction
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.EnergyRequirementComponent

public object EnergyRequirementSystem : RecipeRequirementSystem.Tickable<EnergyRequirementComponent> {

    override fun check(machine: MachineInstance, component: EnergyRequirementComponent, process: RecipeProcess): ProcessResult {
        // Check if energy is available
        return ProcessResult.Success
    }

    override fun onStart(machine: MachineInstance, component: EnergyRequirementComponent, process: RecipeProcess): ProcessResult {
        // Energy might be consumed at start for activation
        return ProcessResult.Success
    }

    override fun acquireTickTransaction(
        machine: MachineInstance,
        component: EnergyRequirementComponent,
        process: RecipeProcess
    ): RecipeTransaction {
        TODO("Not yet implemented")
    }

    override fun onEnd(machine: MachineInstance, component: EnergyRequirementComponent, process: RecipeProcess): ProcessResult {
        // Energy usually not handled at end unless it's generation
        return ProcessResult.Success
    }

}
