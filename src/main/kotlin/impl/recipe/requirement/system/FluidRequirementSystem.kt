package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent

public object FluidRequirementSystem : RecipeRequirementSystem.Tickable<FluidRequirementComponent> {

    override fun start(
        machine: MachineInstance,
        component: FluidRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        TODO("Not yet implemented")
    }

    override fun acquireTickTransaction(
        machine: MachineInstance,
        component: FluidRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        TODO("Not yet implemented")
    }

    override fun onEnd(
        machine: MachineInstance,
        component: FluidRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        TODO("Not yet implemented")
    }

}