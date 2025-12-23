package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureFluidKeyContainer
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import github.kasuminova.prototypemachinery.api.util.scaleByParallelism
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import net.minecraftforge.fluids.FluidStack

public object FluidRequirementSystem : RecipeRequirementSystem.Tickable<FluidRequirementComponent> {

    private fun fluidNameOf(key: PMKey<FluidStack>): String = key.get().fluid.name

    override fun start(
        process: RecipeProcess,
        component: FluidRequirementComponent
    ): RequirementTransaction {
        if (component.inputs.isEmpty()) return noOpSuccess()

        val machine = process.owner
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureFluidKeyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.OUTPUT) }

        if (sources.isEmpty()) {
            return blocked("blocked.fluid.no_sources", listOf(component.id))
        }

        // Pre-check by simulation so Blocked has no side effects.
        for (input in component.inputs) {
            val required = process.scaleByParallelism(input.count)
            if (required <= 0L) continue

            val fluidName = fluidNameOf(input)
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extract(input, remaining, TransactionMode.SIMULATE)
            }

            if (remaining > 0L) {
                return blocked("blocked.fluid.missing_inputs", listOf(component.id, fluidName, remaining.toString()))
            }
        }

        // Execute and record per-container extraction for rollback.
        val extractedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()

        for (input in component.inputs) {
            val required = process.scaleByParallelism(input.count)
            if (required <= 0L) continue

            val fluidName = fluidNameOf(input)
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break

                // Probe quickly to avoid pointless writes.
                val canTake = c.extract(input, remaining, TransactionMode.SIMULATE)
                if (canTake <= 0L) continue

                val took = c.extract(input, remaining, TransactionMode.EXECUTE)
                if (took > 0L) {
                    val map = extractedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[input] = (map[input] ?: 0L) + took
                    remaining -= took
                }
            }

            if (remaining > 0L) {
                // Inconsistent state change between simulate/execute.
                return failure("error.fluid.inconsistent_inputs", listOf(component.id, fluidName, remaining.toString())) {
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
        component: FluidRequirementComponent
    ): RequirementTransaction {
        if (component.inputsPerTick.isEmpty() && component.outputsPerTick.isEmpty()) {
            return noOpSuccess()
        }

        val machine = process.owner
        val sources = if (component.inputsPerTick.isNotEmpty()) {
            machine.structureComponentMap
                .getByInstanceOf(StructureFluidKeyContainer::class.java)
                .filter { it.isAllowedPortMode(PortMode.OUTPUT) }
        } else {
            emptyList()
        }

        val targets = if (component.outputsPerTick.isNotEmpty()) {
            machine.structureComponentMap
                .getByInstanceOf(StructureFluidKeyContainer::class.java)
                .filter { it.isAllowedPortMode(PortMode.INPUT) }
        } else {
            emptyList()
        }

        if (component.inputsPerTick.isNotEmpty() && sources.isEmpty()) {
            return blocked("blocked.fluid.no_sources", listOf(component.id))
        }

        if (component.outputsPerTick.isNotEmpty() && targets.isEmpty()) {
            return blocked("blocked.fluid.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties["ignore_output_full"] as? Boolean) == true

        // Pre-check by simulation so Blocked has no side effects.
        for (input in component.inputsPerTick) {
            val required = process.scaleByParallelism(input.count)
            if (required <= 0L) continue

            val fluidName = fluidNameOf(input)
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extract(input, remaining, TransactionMode.SIMULATE)
            }

            if (remaining > 0L) {
                return blocked("blocked.fluid.missing_inputs", listOf(component.id, fluidName, remaining.toString()))
            }
        }

        if (component.outputsPerTick.isNotEmpty() && !ignoreOutputFull) {
            for (out in component.outputsPerTick) {
                val required = process.scaleByParallelism(out.count)
                if (required <= 0L) continue

                val fluidName = fluidNameOf(out)
                var remaining = required

                for (c in targets) {
                    if (remaining <= 0L) break
                    remaining -= c.insert(out, remaining, TransactionMode.SIMULATE)
                }

                if (remaining > 0L) {
                    return blocked("blocked.fluid.output_full", listOf(component.id, fluidName, remaining.toString()))
                }
            }
        }

        // Execute and record deltas for rollback.
        val extractedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()
        val insertedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()

        for (input in component.inputsPerTick) {
            val required = process.scaleByParallelism(input.count)
            if (required <= 0L) continue

            val fluidName = fluidNameOf(input)
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break

                val canTake = c.extract(input, remaining, TransactionMode.SIMULATE)
                if (canTake <= 0L) continue

                val took = c.extract(input, remaining, TransactionMode.EXECUTE)
                if (took > 0L) {
                    val map = extractedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[input] = (map[input] ?: 0L) + took
                    remaining -= took
                }
            }

            if (remaining > 0L) {
                return failure("error.fluid.inconsistent_tick_inputs", listOf(component.id, fluidName, remaining.toString())) {
                    rollbackInserted(insertedByContainer)
                    rollbackExtracted(extractedByContainer)
                }
            }
        }

        for (out in component.outputsPerTick) {
            val required = process.scaleByParallelism(out.count)
            if (required <= 0L) continue

            val fluidName = fluidNameOf(out)
            var remaining = required

            for (c in targets) {
                if (remaining <= 0L) break

                val canPut = c.insert(out, remaining, TransactionMode.SIMULATE)
                if (canPut <= 0L) continue

                val put = c.insert(out, remaining, TransactionMode.EXECUTE)
                if (put > 0L) {
                    val map = insertedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[out] = (map[out] ?: 0L) + put
                    remaining -= put
                }
            }

            if (remaining > 0L && !ignoreOutputFull) {
                return failure("error.fluid.inconsistent_tick_outputs", listOf(component.id, fluidName, remaining.toString())) {
                    rollbackInserted(insertedByContainer)
                    rollbackExtracted(extractedByContainer)
                }
            }
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                rollbackInserted(insertedByContainer)
                rollbackExtracted(extractedByContainer)
            }
        }
    }

    override fun onEnd(
        process: RecipeProcess,
        component: FluidRequirementComponent
    ): RequirementTransaction {
        if (component.outputs.isEmpty()) return noOpSuccess()

        val machine = process.owner
        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureFluidKeyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.INPUT) }

        if (targets.isEmpty()) {
            return blocked("blocked.fluid.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties["ignore_output_full"] as? Boolean) == true

        // Pre-check by simulation.
        for (out in component.outputs) {
            val required = process.scaleByParallelism(out.count)
            if (required <= 0L) continue

            val fluidName = fluidNameOf(out)
            var remaining = required

            for (c in targets) {
                if (remaining <= 0L) break
                remaining -= c.insert(out, remaining, TransactionMode.SIMULATE)
            }

            if (remaining > 0L) {
                if (ignoreOutputFull) continue
                return blocked("blocked.fluid.output_full", listOf(component.id, fluidName, remaining.toString()))
            }
        }

        val insertedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()

        for (out in component.outputs) {
            val required = process.scaleByParallelism(out.count)
            if (required <= 0L) continue

            val fluidName = fluidNameOf(out)
            var remaining = required

            for (c in targets) {
                if (remaining <= 0L) break

                val canPut = c.insert(out, remaining, TransactionMode.SIMULATE)
                if (canPut <= 0L) continue

                val put = c.insert(out, remaining, TransactionMode.EXECUTE)
                if (put > 0L) {
                    val map = insertedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[out] = (map[out] ?: 0L) + put
                    remaining -= put
                }
            }

            if (remaining > 0L) {
                if (ignoreOutputFull) continue
                return failure("error.fluid.inconsistent_outputs", listOf(component.id, fluidName, remaining.toString())) {
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

    private fun rollbackExtracted(extracted: Map<StructureFluidKeyContainer, Map<PMKey<FluidStack>, Long>>) {
        extracted.forEach { (container, keys) ->
            keys.forEach { (key, amount) ->
                if (amount <= 0L) return@forEach
                // Use unchecked to restore even if PortMode disallows normal insert.
                container.insertUnchecked(key, amount, TransactionMode.EXECUTE)
            }
        }
    }

    private fun rollbackInserted(inserted: Map<StructureFluidKeyContainer, Map<PMKey<FluidStack>, Long>>) {
        inserted.forEach { (container, keys) ->
            keys.forEach { (key, amount) ->
                if (amount <= 0L) return@forEach
                // Use unchecked to restore even if PortMode disallows normal extract.
                container.extractUnchecked(key, amount, TransactionMode.EXECUTE)
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