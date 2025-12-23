package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureItemKeyContainer
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.common.util.Action
import github.kasuminova.prototypemachinery.common.util.IOType
import github.kasuminova.prototypemachinery.common.util.RecipeParallelism
import github.kasuminova.prototypemachinery.common.util.parallelism
import github.kasuminova.prototypemachinery.common.util.scaleByParallelism
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import net.minecraft.item.ItemStack

public object ItemRequirementSystem : RecipeRequirementSystem.Tickable<ItemRequirementComponent> {

    override fun start(process: RecipeProcess, component: ItemRequirementComponent): RequirementTransaction {
        if (component.inputs.isEmpty()) {
            return noOpSuccess()
        }

        val machine = process.owner
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureItemKeyContainer::class.java)
            .filter { it.isAllowedIOType(IOType.OUTPUT) }

        if (sources.isEmpty()) {
            return blocked("blocked.item.no_sources", listOf(component.id))
        }

        val parallels = process.parallelism()
        val needed = aggregateRequired(component.inputs, parallels)

        // Pre-check by simulation so that Blocked has no side effects.
        for ((key, requiredCount) in needed) {
            var remaining = requiredCount

            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extract(key, remaining, Action.SIMULATE)
            }

            if (remaining > 0L) {
                return blocked("blocked.item.missing_inputs", listOf(component.id, remaining.toString()))
            }
        }

        // Execute and record deltas for rollback.
        val extractedByContainer = LinkedHashMap<StructureItemKeyContainer, MutableMap<PMKey<ItemStack>, Long>>()

        for ((key, requiredCount) in needed) {
            var remaining = requiredCount

            for (c in sources) {
                if (remaining <= 0L) break

                val canTake = c.extract(key, remaining, Action.SIMULATE)
                if (canTake <= 0L) continue

                val took = c.extract(key, remaining, Action.EXECUTE)
                if (took > 0L) {
                    val map = extractedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[key] = (map[key] ?: 0L) + took
                    remaining -= took
                }
            }

            if (remaining > 0L) {
                // Should be rare (state changed between simulate/execute). Treat as failure so caller rolls back.
                return failure("error.item.inconsistent_inputs", listOf(component.id, remaining.toString())) {
                    rollbackExtracted(extractedByContainer)
                }
            }
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                rollbackExtracted(extractedByContainer)
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
            .getByInstanceOf(StructureItemKeyContainer::class.java)
            .filter { it.isAllowedIOType(IOType.INPUT) }

        if (targets.isEmpty()) {
            return blocked("blocked.item.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties["ignore_output_full"] as? Boolean) == true

        // Pre-check by simulation so that Blocked has no side effects.
        for (out in component.outputs) {
            val total = process.scaleByParallelism(out.count)
            if (total <= 0L) continue
            var remainingCount = total

            for (c in targets) {
                if (remainingCount <= 0L) break
                remainingCount -= c.insert(out, remainingCount, Action.SIMULATE)
            }

            if (remainingCount > 0L) {
                if (ignoreOutputFull) {
                    // Void the output.
                    continue
                }
                return blocked(
                    "blocked.item.output_full",
                    listOf(component.id, out.get().item.registryName?.toString().orEmpty(), remainingCount.toString())
                )
            }
        }

        val insertedByContainer = LinkedHashMap<StructureItemKeyContainer, MutableMap<PMKey<ItemStack>, Long>>()

        for (out in component.outputs) {
            val total = process.scaleByParallelism(out.count)
            if (total <= 0L) continue
            var remainingCount = total

            for (c in targets) {
                if (remainingCount <= 0L) break

                val canPut = c.insert(out, remainingCount, Action.SIMULATE)
                if (canPut <= 0L) continue

                val inserted = c.insert(out, remainingCount, Action.EXECUTE)
                if (inserted > 0L) {
                    val map = insertedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[out] = (map[out] ?: 0L) + inserted
                    remainingCount -= inserted
                }
            }

            if (remainingCount > 0L) {
                if (ignoreOutputFull) {
                    continue
                }
                return failure(
                    "error.item.inconsistent_outputs",
                    listOf(component.id, out.get().item.registryName?.toString().orEmpty(), remainingCount.toString())
                ) {
                    rollbackInserted(insertedByContainer)
                }
            }
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                rollbackInserted(insertedByContainer)
            }
        }
    }

    private fun aggregateRequired(keys: List<PMKey<ItemStack>>, parallels: Int): Map<PMKey<ItemStack>, Long> {
        val map = LinkedHashMap<PMKey<ItemStack>, Long>()
        for (k in keys) {
            val c = RecipeParallelism.scaleCount(k.count, parallels)
            if (c <= 0L) continue
            map[k] = (map[k] ?: 0L) + c
        }
        return map
    }

    private fun rollbackExtracted(extracted: Map<StructureItemKeyContainer, Map<PMKey<ItemStack>, Long>>) {
        extracted.forEach { (container, keys) ->
            keys.forEach { (key, amount) ->
                if (amount <= 0L) return@forEach
                container.insertUnchecked(key, amount, Action.EXECUTE)
            }
        }
    }

    private fun rollbackInserted(inserted: Map<StructureItemKeyContainer, Map<PMKey<ItemStack>, Long>>) {
        inserted.forEach { (container, keys) ->
            keys.forEach { (key, amount) ->
                if (amount <= 0L) return@forEach
                container.extractUnchecked(key, amount, Action.EXECUTE)
            }
        }
    }

    private fun noOpSuccess(): RequirementTransaction {
        return RequirementTransaction.NoOpSuccess
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