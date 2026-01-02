package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.recipe.process.component.ProcessUnscaledProgressComponent
import github.kasuminova.prototypemachinery.impl.recipe.process.component.ProcessUnscaledProgressComponentType
import github.kasuminova.prototypemachinery.impl.recipe.process.component.RecipeLifecycleStateProcessComponent
import github.kasuminova.prototypemachinery.impl.recipe.process.component.RecipeLifecycleStateProcessComponentType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.overlay.RecipeRequirementOverlay

public object FactoryRecipeProcessorSystem : MachineSystem<FactoryRecipeProcessorComponent> {

    override fun onPreTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {
        // Pre-tick logic if needed
    }

    override fun onTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {
        component.tickProcesses() // Tick executors

        val iterator = component.activeProcesses.iterator()
        while (iterator.hasNext()) {
            val process = iterator.next()

            // Tick process component systems (pre/tick/post) once per machine tick.
            tickProcessComponents(process, Phase.PRE)
            tickProcessComponents(process, Phase.TICK)

            try {
                val lifecycle = getOrCreateLifecycle(process)

                // Start stage: ensure all start() have succeeded before progressing.
                if (!lifecycle.started) {
                    when (val r = executeStart(process)) {
                        is ProcessResult.Success -> lifecycle.started = true
                        is ProcessResult.Blocked -> {
                            process.status = process.status.copy(message = r.reason, isError = false)
                            continue
                        }

                        is ProcessResult.Failure -> {
                            process.status = process.status.copy(message = r.reason, isError = true)
                            iterator.remove()
                            continue
                        }
                    }
                }

                // Completion check before tick: allows finishing a restored process that already reached duration.
                if (isComplete(process)) {
                    when (val r = executeEnd(process)) {
                        is ProcessResult.Success -> {
                            iterator.remove()
                            continue
                        }

                        is ProcessResult.Blocked -> {
                            process.status = process.status.copy(message = r.reason, isError = false)
                            continue
                        }

                        is ProcessResult.Failure -> {
                            process.status = process.status.copy(message = r.reason, isError = true)
                            iterator.remove()
                            continue
                        }
                    }
                }

                // Tick stage (per-tick requirement transactions)
                when (val r = executeTick(process)) {
                    is ProcessResult.Success -> {
                        // Advance progress only when all tick transactions succeeded.
                        val speed = (process.attributeMap.attributes[StandardMachineAttributes.PROCESS_SPEED]?.value ?: 1.0).toFloat()
                        val delta = speed.coerceAtLeast(0.0f)

                        // Also advance unscaled tick counter (+1 per successful machine tick).
                        // This is used by some requirement components that want an unscaled timeline.
                        val unscaled = getOrCreateUnscaledProgress(process)
                        unscaled.ticks += 1.0f

                        process.status = process.status.copy(
                            progress = process.status.progress + delta,
                            message = "Processing",
                            isError = false
                        )
                    }

                    is ProcessResult.Blocked -> {
                        process.status = process.status.copy(message = r.reason, isError = false)
                        continue
                    }

                    is ProcessResult.Failure -> {
                        process.status = process.status.copy(message = r.reason, isError = true)
                        iterator.remove()
                        continue
                    }
                }

                // Completion check after tick.
                if (isComplete(process)) {
                    when (val r = executeEnd(process)) {
                        is ProcessResult.Success -> {
                            iterator.remove()
                            continue
                        }

                        is ProcessResult.Blocked -> {
                            process.status = process.status.copy(message = r.reason, isError = false)
                            continue
                        }

                        is ProcessResult.Failure -> {
                            process.status = process.status.copy(message = r.reason, isError = true)
                            iterator.remove()
                            continue
                        }
                    }
                }
            } finally {
                tickProcessComponents(process, Phase.POST)
            }
        }

        if (component.activeProcesses.isEmpty()) {
            // component.status = RecipeProcessorComponent.ProcessorStatus.IDLE // Already handled in stopRecipe/remove
        }
    }

    override fun onPostTick(machine: MachineInstance, component: FactoryRecipeProcessorComponent) {
        // Post-tick logic
    }

    private enum class Phase {
        PRE, TICK, POST
    }

    @Suppress("UNCHECKED_CAST")
    private fun tickProcessComponents(process: RecipeProcess, phase: Phase) {
        process.components.orderedComponents.forEach { node ->
            val component = node.component
            val type = node.key as RecipeProcessComponentType<RecipeProcessComponent>
            val system = type.system ?: return@forEach

            when (phase) {
                Phase.PRE -> system.onPreTick(process, component)
                Phase.TICK -> system.onTick(process, component)
                Phase.POST -> system.onPostTick(process, component)
            }
        }
    }

    private fun isComplete(process: RecipeProcess): Boolean {
        val duration = process.recipe.durationTicks.coerceAtLeast(0).toFloat()
        if (duration <= 0.0f) return true
        return process.status.progress >= duration
    }

    private fun getOrCreateLifecycle(process: RecipeProcess): RecipeLifecycleStateProcessComponent {
        val existing = process[RecipeLifecycleStateProcessComponentType]
        if (existing != null) return existing

        val created = RecipeLifecycleStateProcessComponentType.createComponent(process)
        process.components.addTail(RecipeLifecycleStateProcessComponentType, created)
        return created
    }

    private fun getOrCreateUnscaledProgress(process: RecipeProcess): ProcessUnscaledProgressComponent {
        val existing = process[ProcessUnscaledProgressComponentType]
        if (existing != null) return existing

        val created = ProcessUnscaledProgressComponentType.createComponent(process)
        process.components.addTail(ProcessUnscaledProgressComponentType, created)
        return created
    }

    private fun executeStart(process: RecipeProcess): ProcessResult {
        return executeStage(
            process = process,
            stage = Stage.START,
        )
    }

    private fun executeTick(process: RecipeProcess): ProcessResult {
        return executeStage(
            process = process,
            stage = Stage.TICK,
        )
    }

    private fun executeEnd(process: RecipeProcess): ProcessResult {
        return executeStage(
            process = process,
            stage = Stage.END,
        )
    }

    private enum class Stage {
        START, TICK, END
    }

    private fun executeStage(process: RecipeProcess, stage: Stage): ProcessResult {
        val transactions = ArrayList<RequirementTransaction>()
        var overall: ProcessResult = ProcessResult.Success

        // Deterministic ordering for stable behavior/testing.
        val entries = process.recipe.requirements.entries
            .sortedBy { it.key.id.toString() }

        outer@ for ((_, list) in entries) {
            for (raw in list) {
                val component = RecipeRequirementOverlay.resolve(process, raw)
                val system = systemFor(component)

                val tx = when (stage) {
                    Stage.START -> system.start(process, component)
                    Stage.END -> system.onEnd(process, component)
                    Stage.TICK -> {
                        if (system is RecipeRequirementSystem.Tickable<*>) {
                            @Suppress("UNCHECKED_CAST")
                            (system as RecipeRequirementSystem.Tickable<RecipeRequirementComponent>).acquireTickTransaction(process, component)
                        } else {
                            noOpSuccess()
                        }
                    }
                }

                transactions.add(tx)

                when (val r = tx.result) {
                    is ProcessResult.Success -> {}
                    is ProcessResult.Blocked -> {
                        overall = r
                        break@outer
                    }

                    is ProcessResult.Failure -> {
                        overall = r
                        break@outer
                    }
                }
            }
        }

        return when (overall) {
            is ProcessResult.Success -> {
                transactions.forEach { it.commit() }
                ProcessResult.Success
            }

            is ProcessResult.Blocked -> {
                // Abort the whole stage to keep it atomic.
                // Blocked should be side-effect free; successful acquisitions MUST be rolled back.
                rollbackAll(transactions)
                overall
            }

            is ProcessResult.Failure -> {
                rollbackAll(transactions)
                overall
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun systemFor(component: RecipeRequirementComponent): RecipeRequirementSystem<RecipeRequirementComponent> {
        return component.type.system as RecipeRequirementSystem<RecipeRequirementComponent>
    }

    private fun rollbackAll(transactions: List<RequirementTransaction>) {
        // Rollback in reverse order.
        for (i in transactions.size - 1 downTo 0) {
            runCatching { transactions[i].rollback() }
        }
    }

    private fun noOpSuccess(): RequirementTransaction {
        return RequirementTransaction.NoOpSuccess
    }

}
