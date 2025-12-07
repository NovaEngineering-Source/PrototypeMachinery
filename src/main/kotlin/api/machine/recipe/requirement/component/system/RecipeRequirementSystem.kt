package github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.RecipeRequirementComponent

public interface RecipeRequirementSystem<C : RecipeRequirementComponent> {

    public fun check(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    public fun onStart(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    public fun onEnd(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    public interface Tickable<C : RecipeRequirementComponent> : RecipeRequirementSystem<C> {

        public fun acquireTickTransaction(machine: MachineInstance, component: C, process: RecipeProcess): RequirementTransaction

    }

}

public interface RequirementTransaction {

    public val result: ProcessResult

    public fun commit()

}