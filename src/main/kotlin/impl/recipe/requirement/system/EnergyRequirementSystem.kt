package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import github.kasuminova.prototypemachinery.api.util.scaleByParallelism
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureEnergyContainer
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent

public object EnergyRequirementSystem : RecipeRequirementSystem.Tickable<EnergyRequirementComponent> {

    override fun start(process: RecipeProcess, component: EnergyRequirementComponent): RequirementTransaction {
        val need = process.scaleByParallelism(component.input)
        if (need <= 0L) return noOpSuccess()

        val machine = process.owner
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureEnergyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.OUTPUT) }

        if (sources.isEmpty()) {
            return blocked("blocked.energy.no_sources", listOf(component.id))
        }

        // Pre-check via simulation so Blocked has no side effects.
        var remaining = need
        for (c in sources) {
            if (remaining <= 0L) break
            val took = c.extractEnergy(remaining, TransactionMode.SIMULATE)
            remaining -= took
        }

        if (remaining > 0L) {
            return blocked("blocked.energy.missing_inputs", listOf(component.id, remaining.toString()))
        }

        // Execute and record per-container extraction for rollback.
        val extractedByContainer = LinkedHashMap<StructureEnergyContainer, Long>()
        remaining = need
        for (c in sources) {
            if (remaining <= 0L) break

            val sim = c.extractEnergy(remaining, TransactionMode.SIMULATE)
            if (sim <= 0L) continue

            val took = c.extractEnergy(remaining, TransactionMode.EXECUTE)
            if (took > 0L) {
                extractedByContainer[c] = (extractedByContainer[c] ?: 0L) + took
                remaining -= took
            }
        }

        if (remaining > 0L) {
            return failure("error.energy.inconsistent_inputs", listOf(component.id, remaining.toString())) {
                rollbackExtracted(extractedByContainer)
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
        component: EnergyRequirementComponent
    ): RequirementTransaction {
        val drainPerTick = process.scaleByParallelism(component.inputPerTick).coerceAtLeast(0L)
        val outputPerTick = process.scaleByParallelism(component.outputPerTick).coerceAtLeast(0L)

        if (drainPerTick <= 0L && outputPerTick <= 0L) return noOpSuccess()

        val machine = process.owner
        val sources = if (drainPerTick > 0L) {
            machine.structureComponentMap
                .getByInstanceOf(StructureEnergyContainer::class.java)
                .filter { it.isAllowedPortMode(PortMode.OUTPUT) }
        } else {
            emptyList()
        }

        val targets = if (outputPerTick > 0L) {
            machine.structureComponentMap
                .getByInstanceOf(StructureEnergyContainer::class.java)
                .filter { it.isAllowedPortMode(PortMode.INPUT) }
        } else {
            emptyList()
        }

        if (drainPerTick > 0L && sources.isEmpty()) {
            return blocked("blocked.energy.no_sources", listOf(component.id))
        }

        if (outputPerTick > 0L && targets.isEmpty()) {
            return blocked("blocked.energy.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties["ignore_output_full"] as? Boolean) == true

        // Pre-check via simulation to keep Blocked side-effect free.
        if (drainPerTick > 0L) {
            var remaining = drainPerTick
            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extractEnergy(remaining, TransactionMode.SIMULATE)
            }
            if (remaining > 0L) {
                return blocked("blocked.energy.missing_inputs", listOf(component.id, remaining.toString()))
            }
        }

        if (outputPerTick > 0L && !ignoreOutputFull) {
            var remaining = outputPerTick
            for (c in targets) {
                if (remaining <= 0L) break
                remaining -= c.insertEnergy(remaining, TransactionMode.SIMULATE)
            }
            if (remaining > 0L) {
                return blocked("blocked.energy.output_full", listOf(component.id, remaining.toString()))
            }
        }

        // Execute and record deltas for rollback.
        val extractedByContainer = LinkedHashMap<StructureEnergyContainer, Long>()
        val insertedByContainer = LinkedHashMap<StructureEnergyContainer, Long>()

        if (drainPerTick > 0L) {
            var remaining = drainPerTick
            for (c in sources) {
                if (remaining <= 0L) break

                val sim = c.extractEnergy(remaining, TransactionMode.SIMULATE)
                if (sim <= 0L) continue

                val took = c.extractEnergy(remaining, TransactionMode.EXECUTE)
                if (took > 0L) {
                    extractedByContainer[c] = (extractedByContainer[c] ?: 0L) + took
                    remaining -= took
                }
            }

            if (remaining > 0L) {
                return failure("error.energy.inconsistent_tick_inputs", listOf(component.id, remaining.toString())) {
                    rollbackInserted(insertedByContainer)
                    rollbackExtracted(extractedByContainer)
                }
            }
        }

        if (outputPerTick > 0L) {
            var remaining = outputPerTick
            for (c in targets) {
                if (remaining <= 0L) break

                val sim = c.insertEnergy(remaining, TransactionMode.SIMULATE)
                if (sim <= 0L) continue

                val accepted = c.insertEnergy(remaining, TransactionMode.EXECUTE)
                if (accepted > 0L) {
                    insertedByContainer[c] = (insertedByContainer[c] ?: 0L) + accepted
                    remaining -= accepted
                }
            }

            if (remaining > 0L && !ignoreOutputFull) {
                return failure("error.energy.inconsistent_tick_outputs", listOf(component.id, remaining.toString())) {
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

    override fun onEnd(process: RecipeProcess, component: EnergyRequirementComponent): RequirementTransaction {
        val out = process.scaleByParallelism(component.output)
        if (out <= 0L) return noOpSuccess()

        val machine = process.owner
        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureEnergyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.INPUT) }

        if (targets.isEmpty()) {
            return blocked("blocked.energy.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties["ignore_output_full"] as? Boolean) == true

        // Pre-check via simulation.
        var remaining = out
        for (c in targets) {
            if (remaining <= 0L) break
            val accepted = c.insertEnergy(remaining, TransactionMode.SIMULATE)
            remaining -= accepted
        }

        if (remaining > 0L && !ignoreOutputFull) {
            return blocked("blocked.energy.output_full", listOf(component.id, remaining.toString()))
        }

        // Execute and record per-container insertion for rollback.
        val insertedByContainer = LinkedHashMap<StructureEnergyContainer, Long>()
        remaining = out
        for (c in targets) {
            if (remaining <= 0L) break

            val sim = c.insertEnergy(remaining, TransactionMode.SIMULATE)
            if (sim <= 0L) continue

            val accepted = c.insertEnergy(remaining, TransactionMode.EXECUTE)
            if (accepted > 0L) {
                insertedByContainer[c] = (insertedByContainer[c] ?: 0L) + accepted
                remaining -= accepted
            }
        }

        if (remaining > 0L && !ignoreOutputFull) {
            return failure("error.energy.inconsistent_outputs", listOf(component.id, remaining.toString())) {
                rollbackInserted(insertedByContainer)
            }
        }

        // If ignoreOutputFull==true, allow partial insertion and ignore leftover.
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                rollbackInserted(insertedByContainer)
            }
        }
    }

    private fun rollbackExtracted(extracted: Map<StructureEnergyContainer, Long>) {
        extracted.forEach { (container, amount) ->
            if (amount <= 0L) return@forEach
            container.insertEnergyUnchecked(amount, TransactionMode.EXECUTE)
        }
    }

    private fun rollbackInserted(inserted: Map<StructureEnergyContainer, Long>) {
        inserted.forEach { (container, amount) ->
            if (amount <= 0L) return@forEach
            container.extractEnergyUnchecked(amount, TransactionMode.EXECUTE)
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
