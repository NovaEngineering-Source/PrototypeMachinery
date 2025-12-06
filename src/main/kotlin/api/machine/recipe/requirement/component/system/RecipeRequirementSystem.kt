package github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess

public interface RecipeRequirementSystem<C : MachineComponent> {

    public fun check(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    public fun onStart(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    public fun onEnd(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

}

public interface TickableRecipeRequirementSystem<C : MachineComponent> : RecipeRequirementSystem<C> {

    public fun onTick(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

}