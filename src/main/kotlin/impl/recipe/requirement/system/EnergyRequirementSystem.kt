package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent

public object EnergyRequirementSystem : RecipeRequirementSystem.Tickable<EnergyRequirementComponent> {

    override fun start(machine: MachineInstance, component: EnergyRequirementComponent, process: RecipeProcess): RequirementTransaction {
        // Check if energy is available and consume if needed
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun rollback() {}
        }
    }

    override fun acquireTickTransaction(
        machine: MachineInstance,
        component: EnergyRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        TODO("Not yet implemented")
    }

    override fun onEnd(machine: MachineInstance, component: EnergyRequirementComponent, process: RecipeProcess): RequirementTransaction {
        // Energy usually not handled at end unless it's generation
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun rollback() {}
        }
    }

}
