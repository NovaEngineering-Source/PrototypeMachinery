package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeExecutor

public interface FactoryRecipeProcessorComponent : MachineComponent {

    public val activeProcesses: MutableCollection<RecipeProcess>

    public val maxConcurrentProcesses: Int

    public val executors: MutableList<RecipeExecutor>

    public fun startProcess(process: RecipeProcess): Boolean

    public fun stopProcess(process: RecipeProcess)

    public fun tickProcesses()

}
