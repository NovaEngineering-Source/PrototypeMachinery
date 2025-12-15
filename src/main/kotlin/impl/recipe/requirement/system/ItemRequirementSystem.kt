package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.common.util.Action
import github.kasuminova.prototypemachinery.common.util.IOType
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureItemContainer
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import net.minecraft.item.ItemStack
import net.minecraftforge.items.ItemHandlerHelper

public object ItemRequirementSystem : RecipeRequirementSystem.Tickable<ItemRequirementComponent> {

    private const val VANILLA_STACK_CAP: Int = Int.MAX_VALUE / 2

    override fun start(process: RecipeProcess, component: ItemRequirementComponent): RequirementTransaction {
        if (component.inputs.isEmpty()) {
            return noOpSuccess()
        }

        val machine = process.owner
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureItemContainer::class.java)
            .filter { it.isAllowedIOType(IOType.OUTPUT) }

        if (sources.isEmpty()) {
            return blocked("blocked.item.no_sources", listOf(component.id))
        }

        val needed = aggregateRequired(component.inputs)

        // Pre-check by simulation so that Blocked has no side effects.
        for ((key, requiredCount) in needed) {
            val rep = representativeStack(key)
            var remaining = requiredCount

            for (c in sources) {
                if (remaining <= 0L) break
                when (val sim = c.extractItem(remaining, Action.SIMULATE) { ItemHandlerHelper.canItemStacksStack(rep, it) }) {
                    is StructureItemContainer.ExtractResult.Success -> remaining -= sim.extracted.count.toLong()
                    is StructureItemContainer.ExtractResult.Empty -> {}
                }
            }

            if (remaining > 0L) {
                return blocked("blocked.item.missing_inputs", listOf(component.id, remaining.toString()))
            }
        }

        // Execute and record snapshots for rollback.
        val snapshots = LinkedHashMap<StructureItemContainer, List<ItemStack>>()

        for ((key, requiredCount) in needed) {
            val rep = representativeStack(key)
            var remaining = requiredCount

            for (c in sources) {
                if (remaining <= 0L) break

                // Probe quickly to avoid needless snapshotting.
                val sim = c.extractItem(remaining, Action.SIMULATE) { ItemHandlerHelper.canItemStacksStack(rep, it) }
                val canTake = (sim as? StructureItemContainer.ExtractResult.Success)?.extracted?.count?.toLong() ?: 0L
                if (canTake <= 0L) continue

                snapshots.putIfAbsent(c, snapshot(c))

                val exec = c.extractItem(remaining, Action.EXECUTE) { ItemHandlerHelper.canItemStacksStack(rep, it) }
                val took = (exec as? StructureItemContainer.ExtractResult.Success)?.extracted?.count?.toLong() ?: 0L
                remaining -= took
            }

            if (remaining > 0L) {
                // Should be rare (state changed between simulate/execute). Treat as failure so caller rolls back.
                return failure("error.item.inconsistent_inputs", listOf(component.id, remaining.toString())) {
                    restoreAll(snapshots)
                }
            }
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                restoreAll(snapshots)
            }
        }
    }

    override fun acquireTickTransaction(
        process: RecipeProcess,
        component: ItemRequirementComponent
    ): RequirementTransaction {
        // Current item IO is handled in start()/onEnd().
        return noOpSuccess()
    }

    override fun onEnd(process: RecipeProcess, component: ItemRequirementComponent): RequirementTransaction {
        if (component.outputs.isEmpty()) {
            return noOpSuccess()
        }

        val machine = process.owner
        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureItemContainer::class.java)
            .filter { it.isAllowedIOType(IOType.INPUT) }

        if (targets.isEmpty()) {
            return blocked("blocked.item.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties["ignore_output_full"] as? Boolean) == true

        // Pre-check by simulation so that Blocked has no side effects.
        for (out in component.outputs) {
            val total = out.count
            if (total <= 0L) continue

            val proto = out.get().also { it.count = 1 }
            var remainingCount = total

            for (c in targets) {
                if (remainingCount <= 0L) break
                remainingCount -= simulateInsertCount(c, proto, remainingCount)
            }

            if (remainingCount > 0L) {
                if (ignoreOutputFull) {
                    // Void the output.
                    continue
                }
                return blocked(
                    "blocked.item.output_full",
                    listOf(component.id, proto.item.registryName?.toString().orEmpty(), remainingCount.toString())
                )
            }
        }

        val snapshots = LinkedHashMap<StructureItemContainer, List<ItemStack>>()

        for (out in component.outputs) {
            val total = out.count
            if (total <= 0L) continue

            val proto = out.get().also { it.count = 1 }
            var remainingCount = total

            for (c in targets) {
                if (remainingCount <= 0L) break

                val canPut = simulateInsertCount(c, proto, remainingCount)
                if (canPut <= 0L) continue

                snapshots.putIfAbsent(c, snapshot(c))

                val inserted = executeInsertCount(c, proto, remainingCount)
                remainingCount -= inserted
            }

            if (remainingCount > 0L) {
                if (ignoreOutputFull) {
                    continue
                }
                return failure(
                    "error.item.inconsistent_outputs",
                    listOf(component.id, proto.item.registryName?.toString().orEmpty(), remainingCount.toString())
                ) {
                    restoreAll(snapshots)
                }
            }
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                restoreAll(snapshots)
            }
        }
    }

    private fun aggregateRequired(keys: List<PMKey<ItemStack>>): Map<PMKey<ItemStack>, Long> {
        val map = LinkedHashMap<PMKey<ItemStack>, Long>()
        for (k in keys) {
            val c = k.count
            if (c <= 0L) continue
            map[k] = (map[k] ?: 0L) + c
        }
        return map
    }

    private fun representativeStack(key: PMKey<ItemStack>): ItemStack {
        // We only need a representative prototype for matching.
        return key.get().also { it.count = 1 }
    }

    private fun simulateInsertCount(container: StructureItemContainer, prototype: ItemStack, amount: Long): Long {
        if (amount <= 0L) return 0L
        val chunk = minOf(amount, VANILLA_STACK_CAP.toLong()).toInt()
        if (chunk <= 0) return 0L

        val stack = prototype.copy().also { it.count = chunk }
        return when (val sim = container.insertItem(stack, Action.SIMULATE)) {
            is StructureItemContainer.InsertResult.Success -> (chunk - sim.remaining.count).coerceAtLeast(0)
            is StructureItemContainer.InsertResult.Full -> 0L
        }.toLong()
    }

    private fun executeInsertCount(container: StructureItemContainer, prototype: ItemStack, amount: Long): Long {
        if (amount <= 0L) return 0L
        val chunk = minOf(amount, VANILLA_STACK_CAP.toLong()).toInt()
        if (chunk <= 0) return 0L

        val stack = prototype.copy().also { it.count = chunk }
        return when (val exec = container.insertItem(stack, Action.EXECUTE)) {
            is StructureItemContainer.InsertResult.Success -> (chunk - exec.remaining.count).coerceAtLeast(0)
            is StructureItemContainer.InsertResult.Full -> 0L
        }.toLong()
    }

    private fun snapshot(container: StructureItemContainer): List<ItemStack> {
        val out = ArrayList<ItemStack>(container.slots)
        for (i in 0 until container.slots) {
            out.add(container.getItem(i).copy())
        }
        return out
    }

    private fun restoreAll(snapshots: Map<StructureItemContainer, List<ItemStack>>) {
        snapshots.forEach { (container, items) ->
            val limit = minOf(container.slots, items.size)
            for (i in 0 until limit) {
                container.setItem(i, items[i])
            }
        }
    }

    private fun noOpSuccess(): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {}
        }
    }

    private fun blocked(reason: String, args: List<String> = emptyList()): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Blocked(reason, args)
            override fun commit() {}
            override fun rollback() {}
        }
    }

    private fun failure(reason: String, args: List<String>, rollbackAction: () -> Unit): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Failure(reason, args)
            override fun commit() {
                error("commit() must not be called when result is Failure")
            }

            override fun rollback() {
                rollbackAction()
            }
        }
    }

}