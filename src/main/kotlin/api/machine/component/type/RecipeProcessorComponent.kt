package github.kasuminova.prototypemachinery.api.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeExecutor

public interface RecipeProcessorComponent : MachineComponent {

    public val activeProcesses: MutableCollection<RecipeProcess>

    public var maxConcurrentProcesses: Int

    public var status: ProcessorStatus

    public val executors: MutableList<RecipeExecutor>

    public fun startProcess(process: RecipeProcess): Boolean

    public fun stopProcess(process: RecipeProcess)

    public fun tickProcesses()

    public data class ProcessorStatus(
        val type: StatusType,
        val message: String = ""
    ) {
        public enum class StatusType {
            IDLE,
            PROCESSING,
            BLOCKED
        }
    }

}
