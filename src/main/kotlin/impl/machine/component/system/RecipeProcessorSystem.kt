package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.RecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess

public object RecipeProcessorSystem : MachineSystem<RecipeProcessorComponent> {

    override fun onPreTick(machine: MachineInstance, component: RecipeProcessorComponent) {
        // Pre-tick logic if needed
    }

    override fun onTick(machine: MachineInstance, component: RecipeProcessorComponent) {
        component.tickProcesses() // Tick executors

        val iterator = component.activeProcesses.iterator()
        while (iterator.hasNext()) {
            val process = iterator.next()

            // Check Completion (Before Tick)
            if (checkCompletion(process)) {
                if (tryFinish(machine, process)) {
                    iterator.remove()
                    continue
                } else {
                    // Cannot complete, maybe mark status
                    continue
                }
            }

            // Process Tick
            // Events would go here (Pre/Post)

            // Ticking Requirements
            if (!tickRequirements(machine, process)) {
                // Requirement failed (e.g. no energy), stop processing this recipe for this tick
                continue
            }

            // Check Completion (After Tick)
            if (checkCompletion(process)) {
                if (tryFinish(machine, process)) {
                    iterator.remove()
                    continue
                }
            }
        }

        if (component.activeProcesses.isEmpty()) {
            // component.status = RecipeProcessorComponent.ProcessorStatus.IDLE // Already handled in stopRecipe/remove
        }
    }

    override fun onPostTick(machine: MachineInstance, component: RecipeProcessorComponent) {
        // Post-tick logic
    }

    private fun checkCompletion(process: RecipeProcess): Boolean {
        // TODO: Check if recipe process is complete (e.g. time elapsed)
        return false
    }

    private fun tryFinish(machine: MachineInstance, process: RecipeProcess): Boolean {
        // Check outputs, consume inputs (if end-consumption), etc.
        // Iterate requirements and call onEnd
        process.recipe.requirements.values.flatten().forEach { req ->
            // We need to find the system for this requirement type and call onEnd
            // This part requires a registry or map of RequirementType -> RequirementSystem
            // For now, we assume we can get it.
        }
        return true
    }

    private fun tickRequirements(machine: MachineInstance, process: RecipeProcess): Boolean {
        // Iterate requirements and call onTick
        process.recipe.requirements.values.flatten().forEach { req ->
            // Call onTick on requirement system
            // if (!system.onTick(machine, component, recipe)) return false
        }
        return true
    }
}
