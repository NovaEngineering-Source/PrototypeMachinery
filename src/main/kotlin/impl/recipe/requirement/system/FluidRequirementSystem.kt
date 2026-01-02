package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeRegistry
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureFluidKeyContainer
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import github.kasuminova.prototypemachinery.api.util.parallelism
import github.kasuminova.prototypemachinery.api.util.probability.ChanceMath
import github.kasuminova.prototypemachinery.api.util.probability.WeightedSampling
import github.kasuminova.prototypemachinery.impl.recipe.process.component.RequirementResolutionProcessComponent
import github.kasuminova.prototypemachinery.impl.recipe.process.component.RequirementResolutionProcessComponentType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import kotlin.math.floor

public object FluidRequirementSystem : RecipeRequirementSystem.Tickable<FluidRequirementComponent> {

    private fun fluidNameOf(key: PMKey<FluidStack>): String = key.get().fluid.name

    @Suppress("UNCHECKED_CAST")
    override fun start(
        process: RecipeProcess,
        component: FluidRequirementComponent
    ): RequirementTransaction {
        val fuzzyInputs = component.fuzzyInputsOrNull()
        if (component.inputs.isEmpty() && fuzzyInputs.isNullOrEmpty()) return noOpSuccess()

        val machine = process.owner
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureFluidKeyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.OUTPUT) }

        if (sources.isEmpty()) {
            return blocked("blocked.fluid.no_sources", listOf(component.id))
        }

        val parallels = process.parallelism().coerceAtLeast(1)
        val chancePercent = component.effectiveChancePercent(process)
        val checkTimes = maxOf(parallels.toLong(), ChanceMath.maxTimes(parallels, chancePercent))

        val resolution = if (!fuzzyInputs.isNullOrEmpty()) getOrCreateResolution(process) else null
        val pendingLockWrites = ArrayList<LockWrite>()

        val resolvedFuzzy: List<ResolvedFuzzy<FluidStack>> = if (fuzzyInputs.isNullOrEmpty()) {
            emptyList()
        } else {
            fuzzyInputs.mapIndexed { idx, group ->
                val lockId = lockId(component.id, stage = "start", groupIndex = idx)
                val existing = resolution?.getLock(lockId) as? PMKey<FluidStack>
                if (existing != null) {
                    ResolvedFuzzy(lockId, existing, group.count)
                } else {
                    val required = safeMul(group.count, checkTimes)
                    val chosen = selectFirstSatisfiable(sources, group.candidates, required)
                        ?: return blocked("blocked.fluid.missing_inputs", listOf(component.id, "?", required.toString()))
                    pendingLockWrites += LockWrite(lockId, chosen)
                    ResolvedFuzzy(lockId, chosen, group.count)
                }
            }
        }

        // Pre-check by simulation so Blocked has no side effects.
        val neededCheck = aggregateRequiredByTimes(component.inputs, checkTimes)
        for (fz in resolvedFuzzy) {
            addCount(neededCheck, fz.key, safeMul(fz.count, checkTimes))
        }

        for ((key, requiredCount) in neededCheck) {
            if (requiredCount <= 0L) continue

            val fluidName = fluidNameOf(key as PMKey<FluidStack>)
            var remaining = requiredCount
            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extract(key, remaining, TransactionMode.SIMULATE)
            }
            if (remaining > 0L) {
                return blocked("blocked.fluid.missing_inputs", listOf(component.id, fluidName, remaining.toString()))
            }
        }

        val lockRollback = LinkedHashMap<String, PMKey<*>?>()
        if (resolution != null) {
            for (w in pendingLockWrites) {
                val prev = resolution.putLock(w.id, w.key)
                lockRollback[w.id] = prev
            }
        }

        val execTimes = ChanceMath.sampleTimes(process.getRandom("fluid:${component.id}:start"), parallels, chancePercent)
        if (execTimes <= 0L) {
            if (lockRollback.isEmpty()) return noOpSuccess()
            return successWithRollback {
                rollbackLocks(resolution, lockRollback)
            }
        }

        val neededExec = aggregateRequiredByTimes(component.inputs, execTimes)
        for (fz in resolvedFuzzy) {
            addCount(neededExec, fz.key, safeMul(fz.count, execTimes))
        }

        val extractedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()
        for ((key, requiredCount) in neededExec) {
            if (requiredCount <= 0L) continue

            val fluidName = fluidNameOf(key)
            var remaining = requiredCount

            for (c in sources) {
                if (remaining <= 0L) break
                val canTake = c.extract(key, remaining, TransactionMode.SIMULATE)
                if (canTake <= 0L) continue

                val took = c.extract(key, remaining, TransactionMode.EXECUTE)
                if (took > 0L) {
                    val map = extractedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[key] = (map[key] ?: 0L) + took
                    remaining -= took
                }
            }

            if (remaining > 0L) {
                return failure("error.fluid.inconsistent_inputs", listOf(component.id, fluidName, remaining.toString())) {
                    rollbackExtracted(extractedByContainer)
                    rollbackLocks(resolution, lockRollback)
                }
            }
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                rollbackExtracted(extractedByContainer)
                rollbackLocks(resolution, lockRollback)
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

        val ignoreOutputFull = (component.properties[RequirementPropertyKeys.IGNORE_OUTPUT_FULL] as? Boolean) == true

        val parallels = process.parallelism().coerceAtLeast(1)
        val chancePercent = component.effectiveChancePercent(process)
        val checkTimes = maxOf(parallels.toLong(), ChanceMath.maxTimes(parallels, chancePercent))
        val tickIndex = tickIndex(process)

        // Pre-check by simulation so Blocked has no side effects.
        for (input in component.inputsPerTick) {
            val required = safeMul(input.count, checkTimes)
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
                val required = safeMul(out.count, checkTimes)
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

        val execTimes = ChanceMath.sampleTimes(process.getRandom("fluid:${component.id}:tick:$tickIndex"), parallels, chancePercent)
        if (execTimes <= 0L) return noOpSuccess()

        val extractedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()
        val insertedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()

        for (input in component.inputsPerTick) {
            val required = safeMul(input.count, execTimes)
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
            val required = safeMul(out.count, execTimes)
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
        val randomOutputs = component.randomOutputsOrNull()
        if (component.outputs.isEmpty() && randomOutputs == null) return noOpSuccess()

        val machine = process.owner
        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureFluidKeyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.INPUT) }

        if (targets.isEmpty()) {
            return blocked("blocked.fluid.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties[RequirementPropertyKeys.IGNORE_OUTPUT_FULL] as? Boolean) == true

        val parallels = process.parallelism().coerceAtLeast(1)
        val chancePercent = component.effectiveChancePercent(process)
        val checkTimes = maxOf(parallels.toLong(), ChanceMath.maxTimes(parallels, chancePercent))

        val neededCheck = aggregateRequiredByTimes(component.outputs, checkTimes)
        if (randomOutputs != null) {
            val worst = randomOutputs.worstCaseKeys()
            for (k in worst) {
                addCount(neededCheck, k, safeMul(k.count, checkTimes))
            }
        }

        for ((out, required) in neededCheck) {
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

        val execTimes = ChanceMath.sampleTimes(process.getRandom("fluid:${component.id}:end"), parallels, chancePercent)
        if (execTimes <= 0L) return noOpSuccess()

        val neededExec = aggregateRequiredByTimes(component.outputs, execTimes)
        if (randomOutputs != null) {
            val rand = randomOutputs.asWeightedSampling()
            for (i in 0 until execTimes) {
                val picks = WeightedSampling.sampleWithoutReplacement(
                    process.getRandom("fluid:${component.id}:end:random:$i"),
                    rand,
                    randomOutputs.pickCount,
                )
                for (k in picks) {
                    addCount(neededExec, k, k.count)
                }
            }
        }

        val insertedByContainer = LinkedHashMap<StructureFluidKeyContainer, MutableMap<PMKey<FluidStack>, Long>>()
        for ((out, required) in neededExec) {
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

    private fun aggregateRequiredByTimes(keys: List<PMKey<FluidStack>>, times: Long): LinkedHashMap<PMKey<FluidStack>, Long> {
        val map = LinkedHashMap<PMKey<FluidStack>, Long>()
        for (k in keys) {
            val c = safeMul(k.count, times)
            if (c <= 0L) continue
            map[k] = (map[k] ?: 0L) + c
        }
        return map
    }

    private fun addCount(map: MutableMap<PMKey<FluidStack>, Long>, key: PMKey<FluidStack>, count: Long) {
        if (count <= 0L) return
        map[key] = (map[key] ?: 0L) + count
    }

    private fun safeMul(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        val r = a * b
        if (r / b != a) return Long.MAX_VALUE
        return r
    }

    private fun tickIndex(process: RecipeProcess): Int {
        val dur = process.recipe.durationTicks.coerceAtLeast(1)
        val p = process.status.progress.toDouble().coerceIn(0.0, 1.0)
        return floor(p * dur.toDouble()).toInt().coerceAtLeast(0)
    }

    private fun selectFirstSatisfiable(
        sources: List<StructureFluidKeyContainer>,
        candidates: List<PMKey<FluidStack>>,
        requiredCount: Long,
    ): PMKey<FluidStack>? {
        if (requiredCount <= 0L) return candidates.firstOrNull()
        for (cand in candidates) {
            var remaining = requiredCount
            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extract(cand, remaining, TransactionMode.SIMULATE)
            }
            if (remaining <= 0L) return cand
        }
        return null
    }

    private fun getOrCreateResolution(process: RecipeProcess): RequirementResolutionProcessComponent {
        val existing = process[RequirementResolutionProcessComponentType]
        if (existing != null) return existing
        val created = RequirementResolutionProcessComponentType.createComponent(process)
        process.components.addTail(RequirementResolutionProcessComponentType, created)
        return created
    }

    private fun rollbackLocks(
        component: RequirementResolutionProcessComponent?,
        rollback: Map<String, PMKey<*>?>,
    ) {
        if (component == null) return
        rollback.forEach { (id, prev) ->
            if (prev == null) {
                component.removeLock(id)
            } else {
                component.putLock(id, prev)
            }
        }
    }

    private fun lockId(requirementId: String, stage: String, groupIndex: Int): String =
        "fluid|$requirementId|$stage|fuzzy|$groupIndex"

    private data class LockWrite(val id: String, val key: PMKey<FluidStack>)

    private data class ResolvedFuzzy<T>(val lockId: String, val key: PMKey<T>, val count: Long)

    @Suppress("UNCHECKED_CAST")
    private fun FluidRequirementComponent.fuzzyInputsOrNull(): List<FuzzyInputGroup<FluidStack>>? {
        return properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<FluidStack>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun FluidRequirementComponent.randomOutputsOrNull(): RandomOutputPool<FluidStack>? {
        return properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<FluidStack>
    }

    private fun FluidRequirementComponent.effectiveChancePercent(process: RecipeProcess): Double {
        val base = (properties[RequirementPropertyKeys.CHANCE] as? Number)?.toDouble() ?: 100.0
        if (!base.isFinite()) return 0.0

        val attr = properties[RequirementPropertyKeys.CHANCE_ATTRIBUTE]
        val attrId = when (attr) {
            is ResourceLocation -> attr
            is String -> runCatching { ResourceLocation(attr) }.getOrNull()
            else -> null
        }

        val multiplier = if (attrId != null) {
            val type: MachineAttributeType? = MachineAttributeRegistry.get(attrId)
            type?.let { process.attributeMap.attributes[it]?.value } ?: 1.0
        } else {
            1.0
        }

        return base * multiplier
    }

    private fun RandomOutputPool<FluidStack>.worstCaseKeys(): List<PMKey<FluidStack>> {
        if (pickCount <= 0) return emptyList()
        return candidates
            .asSequence()
            .map { it.key }
            .sortedByDescending { it.count }
            .take(pickCount)
            .toList()
    }

    private fun RandomOutputPool<FluidStack>.asWeightedSampling(): List<WeightedSampling.Weighted<PMKey<FluidStack>>> {
        return candidates.map { WeightedSampling.Weighted(it.key, it.weight) }
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

    private fun successWithRollback(rollbackAction: () -> Unit): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                rollbackAction()
            }
        }
    }

}