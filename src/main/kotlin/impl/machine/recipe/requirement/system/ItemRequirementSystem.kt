package github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.machine.recipe.requirement.ItemRequirementComponent

public object ItemRequirementSystem : RecipeRequirementSystem.Tickable<ItemRequirementComponent> {

    override fun check(machine: MachineInstance, component: ItemRequirementComponent, process: RecipeProcess): ProcessResult {
        // Check if items are available
        return ProcessResult.Success
    }

    override fun onStart(machine: MachineInstance, component: ItemRequirementComponent, process: RecipeProcess): ProcessResult {
        // TODO: Implement item consumption logic based on recipe requirements
        // Iterate recipe.recipe.requirements, find ITEM type requirements, and try to extract from component
        return ProcessResult.Success
    }

    override fun acquireTickTransaction(
        machine: MachineInstance,
        component: ItemRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        TODO("Not yet implemented")
    }

    override fun onEnd(machine: MachineInstance, component: ItemRequirementComponent, process: RecipeProcess): ProcessResult {
        // Handle item outputs here
        return ProcessResult.Success
    }

}