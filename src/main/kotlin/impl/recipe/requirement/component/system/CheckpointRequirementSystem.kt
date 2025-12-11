package github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementRegistry
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.CheckpointRequirementComponent

/**
 * # CheckpointRequirementSystem
 * # 检查点需求系统
 *
 * Handles the logic for [CheckpointRequirementComponent].
 * It monitors the process progress and enforces the wrapped requirement when the target time is reached.
 *
 * 处理 [CheckpointRequirementComponent] 的逻辑。
 * 它监控进程进度，并在达到目标时间时强制执行包装的需求。
 */
public object CheckpointRequirementSystem : RecipeRequirementSystem.Tickable<CheckpointRequirementComponent> {

    override fun start(
        machine: MachineInstance,
        component: CheckpointRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun rollback() {}
        }
    }

    override fun onEnd(
        machine: MachineInstance,
        component: CheckpointRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun rollback() {}
        }
    }

    override fun acquireTickTransaction(
        machine: MachineInstance,
        component: CheckpointRequirementComponent,
        process: RecipeProcess
    ): RequirementTransaction {
        val currentTick = process.status.progress.toInt()

        // TODO: Implement scaling logic using component.scaleWithProcess
        // For now we use raw time
        val targetTime = component.time

        if (currentTick == targetTime) {
            return executeFullLifecycle(machine, component.requirement, process)
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun rollback() {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : RecipeRequirementComponent> executeFullLifecycle(
        machine: MachineInstance,
        component: T,
        process: RecipeProcess
    ): RequirementTransaction {
        val system = getSystemFor(component) as? RecipeRequirementSystem<T>
            ?: return object : RequirementTransaction {
                override val result = ProcessResult.Failure("error.system_not_found", listOf(component.type.toString()))
                override fun rollback() {}
            }

        val transactions = mutableListOf<RequirementTransaction>()

        // 1. Start
        val startTx = system.start(machine, component, process)
        if (startTx.result !is ProcessResult.Success) {
            return startTx
        }
        transactions.add(startTx)

        // 2. Tick (if tickable)
        if (system is RecipeRequirementSystem.Tickable) {
            val tickTx = system.acquireTickTransaction(machine, component, process)
            if (tickTx.result !is ProcessResult.Success) {
                rollbackAll(transactions)
                return tickTx
            }
            transactions.add(tickTx)
        }

        // 3. End
        val endTx = system.onEnd(machine, component, process)
        if (endTx.result !is ProcessResult.Success) {
            rollbackAll(transactions)
            return endTx
        }
        transactions.add(endTx)

        return object : RequirementTransaction {
            override val result = ProcessResult.Success
            override fun rollback() {
                rollbackAll(transactions)
            }
        }
    }

    private fun rollbackAll(transactions: List<RequirementTransaction>) {
        val iterator = transactions.listIterator(transactions.size)
        while (iterator.hasPrevious()) {
            iterator.previous().rollback()
        }
    }

    private fun getSystemFor(component: RecipeRequirementComponent): RecipeRequirementSystem<*>? {
        return RecipeRequirementRegistry.getSystem(component.type)
    }
}
