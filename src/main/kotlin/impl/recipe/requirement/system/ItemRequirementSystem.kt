package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeRegistry
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.machine.component.container.EnumerableItemKeyContainer
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureItemKeyContainer
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.DynamicItemInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.ItemRequirementMatcherRegistry
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
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

public object ItemRequirementSystem : RecipeRequirementSystem.Tickable<ItemRequirementComponent> {

    override fun start(process: RecipeProcess, component: ItemRequirementComponent): RequirementTransaction {
        val fuzzyInputs = component.fuzzyInputsOrNull()
        val dynamicInputs = component.dynamicInputsOrNull()
        if (component.inputs.isEmpty() && fuzzyInputs.isNullOrEmpty() && dynamicInputs.isNullOrEmpty()) {
            return noOpSuccess()
        }

        val machine = process.owner
        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureItemKeyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.OUTPUT) }

        if (sources.isEmpty()) {
            return blocked("blocked.item.no_sources", listOf(component.id))
        }

        val parallels = process.parallelism().coerceAtLeast(1)
        val chancePercent = component.effectiveChancePercent(process)

        // Capacity check uses an upper bound so the simulated check always covers any sampled execution.
        val checkTimes = maxOf(parallels.toLong(), ChanceMath.maxTimes(parallels, chancePercent))

        val resolution = if (!fuzzyInputs.isNullOrEmpty() || !dynamicInputs.isNullOrEmpty()) getOrCreateResolution(process) else null
        val pendingLockWrites = ArrayList<LockWrite>()

        // Resolve fuzzy inputs (choose + lock) in a side-effect-free way first.
        val resolvedFuzzy: List<ResolvedFuzzy<ItemStack>> = if (fuzzyInputs.isNullOrEmpty()) {
            emptyList()
        } else {
            fuzzyInputs.mapIndexed { idx, group ->
                val lockId = lockId(component.id, stage = "start", groupIndex = idx)
                val existing = resolution?.getLock(lockId) as? PMKey<ItemStack>
                if (existing != null) {
                    ResolvedFuzzy(lockId, existing, group.count)
                } else {
                    val required = safeMul(group.count, checkTimes)
                    val chosen = selectFirstSatisfiable(sources, group.candidates, required)
                        ?: return blocked("blocked.item.missing_inputs", listOf(component.id, required.toString()))
                    pendingLockWrites += LockWrite(lockId, chosen)
                    ResolvedFuzzy(lockId, chosen, group.count)
                }
            }
        }

        // Resolve dynamic inputs (enumerate candidates by matcher -> choose + lock).
        val resolvedDynamic: List<ResolvedFuzzy<ItemStack>> = if (dynamicInputs.isNullOrEmpty()) {
            emptyList()
        } else {
            dynamicInputs.mapIndexed { idx, group ->
                val lockId = lockId(component.id, stage = "start", groupIndex = idx, kind = "dynamic")
                val existing = resolution?.getLock(lockId) as? PMKey<ItemStack>
                if (existing != null) {
                    ResolvedFuzzy(lockId, existing, group.count)
                } else {
                    val matcher = ItemRequirementMatcherRegistry.get(group.matcherId)
                        ?: return blocked("blocked.item.unknown_matcher", listOf(component.id, group.matcherId))

                    val candidates = enumerateDynamicCandidates(sources, group, matcher)
                    if (candidates.isEmpty()) {
                        return blocked("blocked.item.missing_inputs", listOf(component.id, group.count.toString()))
                    }

                    val required = safeMul(group.count, checkTimes)
                    val chosen = selectFirstSatisfiable(sources, candidates, required)
                        ?: return blocked("blocked.item.missing_inputs", listOf(component.id, required.toString()))

                    pendingLockWrites += LockWrite(lockId, chosen)
                    ResolvedFuzzy(lockId, chosen, group.count)
                }
            }
        }

        // Pre-check by simulation so that Blocked has no side effects.
        val neededCheck = aggregateRequiredByTimes(component.inputs, checkTimes)
        for (fz in resolvedFuzzy) {
            addCount(neededCheck, fz.key, safeMul(fz.count, checkTimes))
        }
        for (dz in resolvedDynamic) {
            addCount(neededCheck, dz.key, safeMul(dz.count, checkTimes))
        }

        for ((key, requiredCount) in neededCheck) {
            var remaining = requiredCount
            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extract(key, remaining, TransactionMode.SIMULATE)
            }
            if (remaining > 0L) {
                return blocked("blocked.item.missing_inputs", listOf(component.id, remaining.toString()))
            }
        }

        // Now that simulation passed, we may write locks (these are side effects and must be rollbackable).
        val lockRollback = LinkedHashMap<String, PMKey<*>?>()
        if (resolution != null) {
            for (w in pendingLockWrites) {
                val prev = resolution.putLock(w.id, w.key)
                lockRollback[w.id] = prev
            }
        }

        val execTimes = ChanceMath.sampleTimes(process.getRandom("item:${component.id}:start"), parallels, chancePercent)
        if (execTimes <= 0L) {
            // No IO, but may have lock writes that must be rollbackable.
            if (lockRollback.isEmpty()) return noOpSuccess()
            return successWithRollback {
                rollbackLocks(resolution, lockRollback)
            }
        }

        val neededExec = aggregateRequiredByTimes(component.inputs, execTimes)
        for (fz in resolvedFuzzy) {
            addCount(neededExec, fz.key, safeMul(fz.count, execTimes))
        }
        for (dz in resolvedDynamic) {
            addCount(neededExec, dz.key, safeMul(dz.count, execTimes))
        }

        // Execute and record deltas for rollback.
        val extractedByContainer = LinkedHashMap<StructureItemKeyContainer, MutableMap<PMKey<ItemStack>, Long>>()

        for ((key, requiredCount) in neededExec) {
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
                return failure("error.item.inconsistent_inputs", listOf(component.id, remaining.toString())) {
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
        component: ItemRequirementComponent
    ): RequirementTransaction {
        // Current item IO is handled in start()/onEnd().
        return noOpSuccess()
    }

    override fun onEnd(process: RecipeProcess, component: ItemRequirementComponent): RequirementTransaction {
        val randomOutputs = component.randomOutputsOrNull()
        if (component.outputs.isEmpty() && randomOutputs == null) {
            return noOpSuccess()
        }

        val machine = process.owner
        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureItemKeyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.INPUT) }

        if (targets.isEmpty()) {
            return blocked("blocked.item.no_targets", listOf(component.id))
        }

        val ignoreOutputFull = (component.properties[RequirementPropertyKeys.IGNORE_OUTPUT_FULL] as? Boolean) == true

        val parallels = process.parallelism().coerceAtLeast(1)
        val chancePercent = component.effectiveChancePercent(process)
        val checkTimes = maxOf(parallels.toLong(), ChanceMath.maxTimes(parallels, chancePercent))

        // Pre-check by simulation so that Blocked has no side effects.
        val neededCheck = aggregateRequiredByTimes(component.outputs, checkTimes)
        if (randomOutputs != null) {
            val worst = randomOutputs.worstCaseKeys()
            for (k in worst) {
                addCount(neededCheck, k, safeMul(k.count, checkTimes))
            }
        }

        for ((out, total) in neededCheck) {
            if (total <= 0L) continue
            var remainingCount = total

            for (c in targets) {
                if (remainingCount <= 0L) break
                remainingCount -= c.insert(out, remainingCount, TransactionMode.SIMULATE)
            }

            if (remainingCount > 0L) {
                if (ignoreOutputFull) continue
                return blocked(
                    "blocked.item.output_full",
                    listOf(component.id, out.get().item.registryName?.toString().orEmpty(), remainingCount.toString())
                )
            }
        }

        val execTimes = ChanceMath.sampleTimes(process.getRandom("item:${component.id}:end"), parallels, chancePercent)
        if (execTimes <= 0L) return noOpSuccess()

        val neededExec = aggregateRequiredByTimes(component.outputs, execTimes)
        if (randomOutputs != null) {
            val rand = randomOutputs.asWeightedSampling()
            for (i in 0 until execTimes) {
                val picks = WeightedSampling.sampleWithoutReplacement(
                    process.getRandom("item:${component.id}:end:random:$i"),
                    rand,
                    randomOutputs.pickCount,
                )
                for (k in picks) {
                    addCount(neededExec, k, k.count)
                }
            }
        }

        val insertedByContainer = LinkedHashMap<StructureItemKeyContainer, MutableMap<PMKey<ItemStack>, Long>>()

        for ((out, total) in neededExec) {
            if (total <= 0L) continue
            var remainingCount = total

            for (c in targets) {
                if (remainingCount <= 0L) break

                val canPut = c.insert(out, remainingCount, TransactionMode.SIMULATE)
                if (canPut <= 0L) continue

                val inserted = c.insert(out, remainingCount, TransactionMode.EXECUTE)
                if (inserted > 0L) {
                    val map = insertedByContainer.getOrPut(c) { LinkedHashMap() }
                    map[out] = (map[out] ?: 0L) + inserted
                    remainingCount -= inserted
                }
            }

            if (remainingCount > 0L) {
                if (ignoreOutputFull) continue
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

    private fun aggregateRequiredByTimes(keys: List<PMKey<ItemStack>>, times: Long): LinkedHashMap<PMKey<ItemStack>, Long> {
        val map = LinkedHashMap<PMKey<ItemStack>, Long>()
        for (k in keys) {
            val c = safeMul(k.count, times)
            if (c <= 0L) continue
            map[k] = (map[k] ?: 0L) + c
        }
        return map
    }

    private fun addCount(map: MutableMap<PMKey<ItemStack>, Long>, key: PMKey<ItemStack>, count: Long) {
        if (count <= 0L) return
        map[key] = (map[key] ?: 0L) + count
    }

    private fun safeMul(a: Long, b: Long): Long {
        if (a == 0L || b == 0L) return 0L
        val r = a * b
        if (r / b != a) return Long.MAX_VALUE
        return r
    }

    private fun selectFirstSatisfiable(
        sources: List<StructureItemKeyContainer>,
        candidates: List<PMKey<ItemStack>>,
        requiredCount: Long,
    ): PMKey<ItemStack>? {
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

    private fun lockId(requirementId: String, stage: String, groupIndex: Int, kind: String = "fuzzy"): String =
        "item|$requirementId|$stage|$kind|$groupIndex"

    private data class LockWrite(val id: String, val key: PMKey<ItemStack>)

    private data class ResolvedFuzzy<T>(val lockId: String, val key: PMKey<T>, val count: Long)

    @Suppress("UNCHECKED_CAST")
    private fun ItemRequirementComponent.fuzzyInputsOrNull(): List<FuzzyInputGroup<ItemStack>>? {
        return properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<ItemStack>>
    }

    @Suppress("UNCHECKED_CAST")
    private fun ItemRequirementComponent.dynamicInputsOrNull(): List<DynamicItemInputGroup>? {
        return properties[RequirementPropertyKeys.DYNAMIC_ITEM_INPUTS] as? List<DynamicItemInputGroup>
    }

    private fun enumerateDynamicCandidates(
        sources: List<StructureItemKeyContainer>,
        group: DynamicItemInputGroup,
        matcher: ItemRequirementMatcherRegistry.Matcher,
    ): List<PMKey<ItemStack>> {
        val seen = LinkedHashSet<PMKey<ItemStack>>()
        val pattern = group.pattern.get().copy().also { it.count = 1 }

        // Enumerate concrete variants from all enumerable sources.
        for (c in sources) {
            val enum = c as? EnumerableItemKeyContainer ?: continue
            for (k in enum.getAllKeysSnapshot()) {
                if (seen.size >= group.maxCandidates) break
                val cand = k.get()
                if (cand.isEmpty) continue
                val candCopy = cand.copy().also { it.count = 1 }
                if (matcher.matches(candCopy, pattern)) {
                    seen += k
                }
            }
            if (seen.size >= group.maxCandidates) break
        }

        if (seen.isEmpty()) return emptyList()

        // Deterministic ordering: registryName + meta + hash.
        return seen.toList().sortedWith(
            compareBy<PMKey<ItemStack>>(
                { it.get().item.registryName?.toString().orEmpty() },
                { it.get().metadata },
                { it.hashCode() },
            )
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun ItemRequirementComponent.randomOutputsOrNull(): RandomOutputPool<ItemStack>? {
        return properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<ItemStack>
    }

    private fun ItemRequirementComponent.effectiveChancePercent(process: RecipeProcess): Double {
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

    private fun RandomOutputPool<ItemStack>.worstCaseKeys(): List<PMKey<ItemStack>> {
        if (pickCount <= 0) return emptyList()
        return candidates
            .asSequence()
            .map { it.key }
            .sortedByDescending { it.count }
            .take(pickCount)
            .toList()
    }

    private fun RandomOutputPool<ItemStack>.asWeightedSampling(): List<WeightedSampling.Weighted<PMKey<ItemStack>>> {
        return candidates.map { WeightedSampling.Weighted(it.key, it.weight) }
    }

    private fun rollbackExtracted(extracted: Map<StructureItemKeyContainer, Map<PMKey<ItemStack>, Long>>) {
        extracted.forEach { (container, keys) ->
            keys.forEach { (key, amount) ->
                if (amount <= 0L) return@forEach
                container.insertUnchecked(key, amount, TransactionMode.EXECUTE)
            }
        }
    }

    private fun rollbackInserted(inserted: Map<StructureItemKeyContainer, Map<PMKey<ItemStack>, Long>>) {
        inserted.forEach { (container, keys) ->
            keys.forEach { (key, amount) ->
                if (amount <= 0L) return@forEach
                container.extractUnchecked(key, amount, TransactionMode.EXECUTE)
            }
        }
    }

    private fun noOpSuccess(): RequirementTransaction {
        return RequirementTransaction.NoOpSuccess
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