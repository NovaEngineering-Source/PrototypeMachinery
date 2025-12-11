package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent

public object ItemRequirementSystem : RecipeRequirementSystem.Tickable<ItemRequirementComponent> {

    override fun start(machine: MachineInstance, component: ItemRequirementComponent, process: RecipeProcess): RequirementTransaction {
        // Check if items are available and consume
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun rollback() {}
        }
    }

    override fun acquireTickTransaction(
        machine: MachineInstance,
        component: ItemRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        TODO("Not yet implemented")
    }

    override fun onEnd(machine: MachineInstance, component: ItemRequirementComponent, process: RecipeProcess): RequirementTransaction {
        // Handle item outputs here
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun rollback() {}
        }
    }

}