package github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.recipe.process.component.ProcessUnscaledProgressComponentType
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
        process: RecipeProcess,
        component: CheckpointRequirementComponent
    ): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {}
        }
    }

    override fun onEnd(
        process: RecipeProcess,
        component: CheckpointRequirementComponent
    ): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {}
        }
    }

    override fun acquireTickTransaction(
        process: RecipeProcess,
        component: CheckpointRequirementComponent
    ): RequirementTransaction {
        val target = component.time.toFloat()

        val (current, next) = if (component.scaleWithProcess) {
            // Scaled timeline: progress advances by PROCESS_SPEED.
            // Use a threshold-crossing check to avoid missing the checkpoint when speed > 1.
            val speed = (process.attributeMap.attributes[StandardMachineAttributes.PROCESS_SPEED]?.value ?: 1.0).toFloat()
            val delta = speed.coerceAtLeast(0.0f)
            val cur = process.status.progress
            cur to (cur + delta)
        } else {
            // Unscaled timeline: +1 per successful tick.
            // This is tracked by ProcessUnscaledProgressComponent (maintained by the processor).
            val cur = (process[ProcessUnscaledProgressComponentType]?.ticks ?: 0.0f)
            cur to (cur + 1.0f)
        }

        // Trigger when crossing the target tick.
        if (current < target && next >= target) {
            return executeFullLifecycle(process, component.requirement)
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {}
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : RecipeRequirementComponent> executeFullLifecycle(
        process: RecipeProcess,
        component: T
    ): RequirementTransaction {
        val system = getSystemFor(component) as? RecipeRequirementSystem<T>
            ?: return failure(ProcessResult.Failure("error.system_not_found", listOf(component.type.toString())))

        val transactions = mutableListOf<RequirementTransaction>()
        var overallResult: ProcessResult = ProcessResult.Success

        // 1. Start
        val startTx = system.start(process, component)
        when (val r = startTx.result) {
            is ProcessResult.Failure -> return failure(r) { startTx.rollback() }
            is ProcessResult.Blocked -> overallResult = r
            is ProcessResult.Success -> {}
        }
        transactions.add(startTx)

        // 2. Tick (if tickable)
        if (system is RecipeRequirementSystem.Tickable) {
            val tickTx = system.acquireTickTransaction(process, component)
            when (val r = tickTx.result) {
                is ProcessResult.Failure -> {
                    return failure(r) {
                        tickTx.rollback()
                        rollbackAll(transactions)
                    }
                }

                is ProcessResult.Blocked -> if (overallResult is ProcessResult.Success) overallResult = r
                is ProcessResult.Success -> {}
            }
            transactions.add(tickTx)
        }

        // 3. End
        val endTx = system.onEnd(process, component)
        when (val r = endTx.result) {
            is ProcessResult.Failure -> {
                return failure(r) {
                    endTx.rollback()
                    rollbackAll(transactions)
                }
            }

            is ProcessResult.Blocked -> if (overallResult is ProcessResult.Success) overallResult = r
            is ProcessResult.Success -> {}
        }
        transactions.add(endTx)

        return object : RequirementTransaction {
            override val result: ProcessResult = overallResult
            override fun commit() {
                commitAll(transactions)
            }

            override fun rollback() {
                rollbackAll(transactions)
            }
        }
    }

    private fun failure(result: ProcessResult.Failure, rollbackAction: () -> Unit = {}): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = result
            override fun commit() {
                error("commit() must not be called when result is Failure")
            }

            override fun rollback() {
                rollbackAction()
            }
        }
    }

    private fun commitAll(transactions: List<RequirementTransaction>) {
        transactions.forEach { it.commit() }
    }

    private fun rollbackAll(transactions: List<RequirementTransaction>) {
        val iterator = transactions.listIterator(transactions.size)
        while (iterator.hasPrevious()) {
            iterator.previous().rollback()
        }
    }

    private fun getSystemFor(component: RecipeRequirementComponent): RecipeRequirementSystem<*>? {
        return component.type.system
    }
}
