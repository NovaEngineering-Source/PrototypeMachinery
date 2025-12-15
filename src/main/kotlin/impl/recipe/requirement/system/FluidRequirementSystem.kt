package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.common.util.Action
import github.kasuminova.prototypemachinery.common.util.IOType
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureFluidContainer
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidStack

public object FluidRequirementSystem : RecipeRequirementSystem.Tickable<FluidRequirementComponent> {

    override fun start(
        process: RecipeProcess,
        component: FluidRequirementComponent
    ): RequirementTransaction {
        if (component.inputs.isEmpty()) return noOpSuccess()

        val machine = process.owner
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureFluidContainer::class.java)
            .filter { it.isAllowedIOType(IOType.OUTPUT) }

        if (sources.isEmpty()) {
            return blocked("blocked.fluid.no_sources", listOf(component.id))
        }

        // Pre-check by simulation so Blocked has no side effects.
        for (input in component.inputs) {
            val required = input.count
            if (required <= 0L) continue

            val prototype = input.get()
            val fluid = prototype.fluid
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break
                when (val sim = c.extractFluid(fluid, remaining, Action.SIMULATE)) {
                    is StructureFluidContainer.ExtractResult.Success -> remaining -= sim.amount
                    is StructureFluidContainer.ExtractResult.Empty -> {}
                }
            }

            if (remaining > 0L) {
                return blocked("blocked.fluid.missing_inputs", listOf(component.id, fluid.name, remaining.toString()))
            }
        }

        // Execute and record per-container extraction for rollback.
        val extractedByContainer = LinkedHashMap<StructureFluidContainer, MutableMap<Fluid, Long>>()

        for (input in component.inputs) {
            val required = input.count
            if (required <= 0L) continue

            val prototype = input.get()
            val fluid = prototype.fluid
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break

                // Probe quickly to avoid pointless writes.
                val sim = c.extractFluid(fluid, remaining, Action.SIMULATE)
                val canTake = (sim as? StructureFluidContainer.ExtractResult.Success)?.amount ?: 0L
                if (canTake <= 0L) continue

                val exec = c.extractFluid(fluid, remaining, Action.EXECUTE)
                val took = (exec as? StructureFluidContainer.ExtractResult.Success)?.amount ?: 0L
                if (took > 0L) {
                    val map = extractedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[fluid] = (map[fluid] ?: 0L) + took
                    remaining -= took
                }
            }

            if (remaining > 0L) {
                // Inconsistent state change between simulate/execute.
                return failure("error.fluid.inconsistent_inputs", listOf(component.id, fluid.name, remaining.toString())) {
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
                .getByInstanceOf(StructureFluidContainer::class.java)
                .filter { it.isAllowedIOType(IOType.OUTPUT) }
        } else {
            emptyList()
        }

        val targets = if (component.outputsPerTick.isNotEmpty()) {
            machine.structureComponentMap
                .getByInstanceOf(StructureFluidContainer::class.java)
                .filter { it.isAllowedIOType(IOType.INPUT) }
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
            val required = input.count
            if (required <= 0L) continue

            val prototype = input.get()
            val fluid = prototype.fluid
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break
                when (val sim = c.extractFluid(fluid, remaining, Action.SIMULATE)) {
                    is StructureFluidContainer.ExtractResult.Success -> remaining -= sim.amount
                    is StructureFluidContainer.ExtractResult.Empty -> {}
                }
            }

            if (remaining > 0L) {
                return blocked("blocked.fluid.missing_inputs", listOf(component.id, fluid.name, remaining.toString()))
            }
        }

        if (component.outputsPerTick.isNotEmpty() && !ignoreOutputFull) {
            for (out in component.outputsPerTick) {
                val required = out.count
                if (required <= 0L) continue

                val prototype = out.get()
                val fluid = prototype.fluid
                var remaining = required

                for (c in targets) {
                    if (remaining <= 0L) break
                    when (val sim = c.insertFluid(prototype, remaining, Action.SIMULATE)) {
                        is StructureFluidContainer.InsertResult.Success -> remaining = sim.remaining
                        is StructureFluidContainer.InsertResult.Full -> {}
                    }
                }

                if (remaining > 0L) {
                    return blocked("blocked.fluid.output_full", listOf(component.id, fluid.name, remaining.toString()))
                }
            }
        }

        // Execute and record deltas for rollback.
        val extractedByContainer = LinkedHashMap<StructureFluidContainer, MutableMap<Fluid, Long>>()
        val insertedByContainer = LinkedHashMap<StructureFluidContainer, MutableMap<Fluid, Long>>()

        for (input in component.inputsPerTick) {
            val required = input.count
            if (required <= 0L) continue

            val prototype = input.get()
            val fluid = prototype.fluid
            var remaining = required

            for (c in sources) {
                if (remaining <= 0L) break

                val sim = c.extractFluid(fluid, remaining, Action.SIMULATE)
                val canTake = (sim as? StructureFluidContainer.ExtractResult.Success)?.amount ?: 0L
                if (canTake <= 0L) continue

                val exec = c.extractFluid(fluid, remaining, Action.EXECUTE)
                val took = (exec as? StructureFluidContainer.ExtractResult.Success)?.amount ?: 0L
                if (took > 0L) {
                    val map = extractedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[fluid] = (map[fluid] ?: 0L) + took
                    remaining -= took
                }
            }

            if (remaining > 0L) {
                return failure("error.fluid.inconsistent_tick_inputs", listOf(component.id, fluid.name, remaining.toString())) {
                    rollbackInserted(insertedByContainer)
                    rollbackExtracted(extractedByContainer)
                }
            }
        }

        for (out in component.outputsPerTick) {
            val required = out.count
            if (required <= 0L) continue

            val prototype = out.get()
            val fluid = prototype.fluid
            var remaining = required

            for (c in targets) {
                if (remaining <= 0L) break

                val sim = c.insertFluid(prototype, remaining, Action.SIMULATE)
                val remainingAfterSim = (sim as? StructureFluidContainer.InsertResult.Success)?.remaining ?: remaining
                val canPut = remaining - remainingAfterSim
                if (canPut <= 0L) continue

                val exec = c.insertFluid(prototype, remaining, Action.EXECUTE)
                val remainingAfterExec = (exec as? StructureFluidContainer.InsertResult.Success)?.remaining ?: remaining
                val put = remaining - remainingAfterExec
                if (put > 0L) {
                    val map = insertedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[fluid] = (map[fluid] ?: 0L) + put
                    remaining = remainingAfterExec
                }
            }

            if (remaining > 0L && !ignoreOutputFull) {
                return failure("error.fluid.inconsistent_tick_outputs", listOf(component.id, fluid.name, remaining.toString())) {
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
            .getByInstanceOf(StructureFluidContainer::class.java)
            .filter { it.isAllowedIOType(IOType.INPUT) }

        if (targets.isEmpty()) {
            return blocked("blocked.fluid.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties["ignore_output_full"] as? Boolean) == true

        // Pre-check by simulation.
        for (out in component.outputs) {
            val required = out.count
            if (required <= 0L) continue

            val prototype = out.get()
            val fluid = prototype.fluid
            var remaining = required

            for (c in targets) {
                if (remaining <= 0L) break
                when (val sim = c.insertFluid(prototype, remaining, Action.SIMULATE)) {
                    is StructureFluidContainer.InsertResult.Success -> remaining = sim.remaining
                    is StructureFluidContainer.InsertResult.Full -> {}
                }
            }

            if (remaining > 0L) {
                if (ignoreOutputFull) continue
                return blocked("blocked.fluid.output_full", listOf(component.id, fluid.name, remaining.toString()))
            }
        }

        val insertedByContainer = LinkedHashMap<StructureFluidContainer, MutableMap<Fluid, Long>>()

        for (out in component.outputs) {
            val required = out.count
            if (required <= 0L) continue

            val prototype = out.get()
            val fluid = prototype.fluid
            var remaining = required

            for (c in targets) {
                if (remaining <= 0L) break

                val sim = c.insertFluid(prototype, remaining, Action.SIMULATE)
                val remainingAfterSim = (sim as? StructureFluidContainer.InsertResult.Success)?.remaining ?: remaining
                val canPut = remaining - remainingAfterSim
                if (canPut <= 0L) continue

                val exec = c.insertFluid(prototype, remaining, Action.EXECUTE)
                val remainingAfterExec = (exec as? StructureFluidContainer.InsertResult.Success)?.remaining ?: remaining
                val put = remaining - remainingAfterExec
                if (put > 0L) {
                    val map = insertedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[fluid] = (map[fluid] ?: 0L) + put
                    remaining = remainingAfterExec
                }
            }

            if (remaining > 0L) {
                if (ignoreOutputFull) continue
                return failure("error.fluid.inconsistent_outputs", listOf(component.id, fluid.name, remaining.toString())) {
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

    private fun rollbackExtracted(extracted: Map<StructureFluidContainer, Map<Fluid, Long>>) {
        extracted.forEach { (container, fluids) ->
            fluids.forEach { (fluid, amount) ->
                if (amount <= 0L) return@forEach
                // Use unchecked to restore even if IOType disallows normal insert.
                container.insertFluidUnchecked(FluidStack(fluid, 1), amount, Action.EXECUTE)
            }
        }
    }

    private fun rollbackInserted(inserted: Map<StructureFluidContainer, Map<Fluid, Long>>) {
        inserted.forEach { (container, fluids) ->
            fluids.forEach { (fluid, amount) ->
                if (amount <= 0L) return@forEach
                // Use unchecked to restore even if IOType disallows normal extract.
                container.extractFluidUnchecked(fluid, amount, Action.EXECUTE)
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