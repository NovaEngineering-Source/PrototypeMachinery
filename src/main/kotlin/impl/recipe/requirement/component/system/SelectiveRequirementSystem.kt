package github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.api.recipe.selective.SelectiveContext
import github.kasuminova.prototypemachinery.api.recipe.selective.SelectiveModifierRegistry
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeInstanceImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeModifierImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.OverlayMachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.process.component.SelectiveStateProcessComponent
import github.kasuminova.prototypemachinery.impl.recipe.process.component.SelectiveStateProcessComponentType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.SelectiveRequirementComponent
import net.minecraft.util.ResourceLocation
import org.jetbrains.annotations.ApiStatus

/**
 * # SelectiveRequirementSystem
 * # 选择性需求系统
 *
 * A selective wrapper:
 * - At start, tries candidates in order and selects the first one whose start() returns Success.
 * - Once selected, the same candidate is used for the rest of the process.
 * - If no candidate is available at selection time, the selection stays disabled for the whole process.
 * - When committed, invokes optional modifier hooks (by id) to mutate the running process.
 *
 * 选择性包装：
 * - start 阶段按顺序尝试候选，选择第一个 start() 返回 Success 的需求。
 * - 一旦选定，将在后续整个配方过程中只使用该候选。
 * - 若选择阶段没有可用候选，则本次配方全过程都不会启用该选择。
 * - commit 时可选触发 modifier（按 id）对进程进行变更（用于实现“催化剂效果”等）。
 */
@ApiStatus.Experimental
public object SelectiveRequirementSystem : RecipeRequirementSystem.Tickable<SelectiveRequirementComponent> {

    override fun start(process: RecipeProcess, component: SelectiveRequirementComponent): RequirementTransaction {
        val machine = process.owner
        val state = getOrCreateState(process)

        // If already decided (e.g. re-entry), just no-op.
        val existing = state.getSelectedIndex(component.id)
        if (existing != null) {
            return noOpSuccess()
        }

        val selection = selectCandidateForStart(process, component)
        state.setSelectedIndex(component.id, selection.selectedIndex)

        if (selection.selectedIndex < 0) {
            // disabled
            return noOpSuccess()
        }

        val startTx = selection.startTx
            ?: return failure(ProcessResult.Failure("error.selective.missing_start_tx", listOf(component.id)))

        return object : RequirementTransaction {
            override val result: ProcessResult = startTx.result

            private var committed = false

            override fun commit() {
                if (committed) return
                committed = true

                startTx.commit()

                if (component.modifierIds.isEmpty()) return

                val ctx = SelectiveContextImpl(
                    machine = machine,
                    process = process,
                    selectionId = component.id,
                    state = state,
                )

                component.modifierIds.forEachIndexed { idx, modifierId ->
                    val modifier = SelectiveModifierRegistry.get(modifierId)
                        ?: error("Unknown selective modifier id: $modifierId")
                    ctx.currentModifierOrdinal = idx
                    ctx.currentModifierId = modifierId
                    modifier.apply(ctx)
                }
            }

            override fun rollback() {
                // Best-effort rollback for start stage.
                cleanupAppliedModifiers(state, component.id, process)
                startTx.rollback()
            }
        }
    }

    override fun acquireTickTransaction(
        process: RecipeProcess,
        component: SelectiveRequirementComponent
    ): RequirementTransaction {
        val state = getOrCreateState(process)
        val selectedIndex = state.getSelectedIndex(component.id) ?: -1
        if (selectedIndex < 0) {
            return noOpSuccess()
        }

        val chosen = component.candidates.getOrNull(selectedIndex)
            ?: return failure(ProcessResult.Failure("error.selective.invalid_selection", listOf(component.id, selectedIndex.toString())))

        val system = systemFor(chosen)
            ?: return failure(ProcessResult.Failure("error.system_not_found", listOf(chosen.type.toString())))

        if (system !is RecipeRequirementSystem.Tickable) {
            return noOpSuccess()
        }

        val tx = system.acquireTickTransaction(process, chosen)
        return wrap(tx)
    }

    override fun onEnd(process: RecipeProcess, component: SelectiveRequirementComponent): RequirementTransaction {
        val state = getOrCreateState(process)
        val selectedIndex = state.getSelectedIndex(component.id) ?: -1
        if (selectedIndex < 0) {
            return noOpSuccess()
        }

        val chosen = component.candidates.getOrNull(selectedIndex)
            ?: return failure(ProcessResult.Failure("error.selective.invalid_selection", listOf(component.id, selectedIndex.toString())))

        val system = systemFor(chosen)
            ?: return failure(ProcessResult.Failure("error.system_not_found", listOf(chosen.type.toString())))

        val tx = system.onEnd(process, chosen)

        return object : RequirementTransaction {
            override val result: ProcessResult = tx.result

            override fun commit() {
                tx.commit()
                cleanupAppliedModifiers(state, component.id, process)
            }

            override fun rollback() {
                tx.rollback()
                cleanupAppliedModifiers(state, component.id, process)
            }
        }
    }

    private data class StartSelection(
        val selectedIndex: Int,
        val startTx: RequirementTransaction? = null,
    )

    private fun selectCandidateForStart(
        process: RecipeProcess,
        component: SelectiveRequirementComponent,
    ): StartSelection {
        if (component.candidates.isEmpty()) {
            return StartSelection(selectedIndex = -1)
        }

        component.candidates.forEachIndexed { idx, candidate ->
            val system = systemFor(candidate) ?: return@forEachIndexed

            val tx = system.start(process, candidate)

            when (tx.result) {
                is ProcessResult.Success -> return StartSelection(idx, tx)
                is ProcessResult.Failure -> tx.rollback()
                is ProcessResult.Blocked -> {
                    // Selective wrapper must not block the whole process.
                    tx.commit()
                }
            }
        }

        return StartSelection(selectedIndex = -1)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : RecipeRequirementComponent> systemFor(component: T): RecipeRequirementSystem<T>? {
        return component.type.system as? RecipeRequirementSystem<T>
    }

    private fun getOrCreateState(process: RecipeProcess): SelectiveStateProcessComponent {
        val existing = process[SelectiveStateProcessComponentType]
        if (existing != null) {
            return existing
        }

        val created = SelectiveStateProcessComponentType.createComponent(process)
        process.components.addTail(SelectiveStateProcessComponentType, created as RecipeProcessComponent)
        return created
    }

    private fun wrap(tx: RequirementTransaction): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = tx.result
            override fun commit() = tx.commit()
            override fun rollback() = tx.rollback()
        }
    }

    private fun noOpSuccess(): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {}
        }
    }

    private fun failure(result: ProcessResult.Failure): RequirementTransaction {
        return object : RequirementTransaction {
            override val result: ProcessResult = result
            override fun commit() {
                error("commit() must not be called when result is Failure")
            }

            override fun rollback() {
                // no-op
            }
        }
    }

    private fun cleanupAppliedModifiers(state: SelectiveStateProcessComponent, selectionId: String, process: RecipeProcess) {
        val refs = state.consumeAppliedModifiers(selectionId)
        if (refs.isEmpty()) return

        refs.forEach { (attrId, modifierId) ->
            val attrType = parseAttributeType(attrId)
            val instance = getAttributeInstance(process.attributeMap, attrType) ?: return@forEach
            instance.removeModifier(modifierId)
        }
    }

    private fun parseAttributeType(id: String): MachineAttributeType {
        // Best-effort: try to find an existing attribute instance key by id, otherwise give up.
        // We cannot construct a MachineAttributeType from id without a registry (yet).
        return object : MachineAttributeType {
            override val id: ResourceLocation = ResourceLocation(id)
            override val name: String = id
        }
    }

    private fun getAttributeInstance(map: MachineAttributeMap, type: MachineAttributeType): MachineAttributeInstance? {
        return map.attributes.entries.firstOrNull { it.key.id == type.id }?.value
    }

    private class SelectiveContextImpl(
        override val machine: MachineInstance,
        override val process: RecipeProcess,
        override val selectionId: String,
        private val state: SelectiveStateProcessComponent,
    ) : SelectiveContext {

        var currentModifierOrdinal: Int = 0
        var currentModifierId: String = ""

        override fun putProcessAttributeModifier(attribute: MachineAttributeType, modifier: MachineAttributeModifier) {
            val uniqueId = "selective/${selectionId}/${currentModifierOrdinal}/${currentModifierId}/${modifier.id}".replace(' ', '_')
            val actualModifier = when {
                modifier.id == uniqueId -> modifier
                modifier is MachineAttributeModifierImpl -> modifier.copy(id = uniqueId)
                else -> wrapModifierWithId(modifier, uniqueId)
            }

            val instance = getOrCreateAttributeInstance(process.attributeMap, attribute)
            instance.removeModifier(actualModifier.id)
            instance.addModifier(actualModifier)

            state.recordAppliedModifier(selectionId, attribute, actualModifier.id)
        }

        private fun getOrCreateAttributeInstance(map: MachineAttributeMap, type: MachineAttributeType): MachineAttributeInstance {
            return when (map) {
                is MachineAttributeMapImpl -> map.getOrCreateAttribute(type, defaultBase = 1.0)
                is OverlayMachineAttributeMapImpl -> map.getOrCreateAttribute(type, defaultBase = 1.0)
                else -> {
                    val attrs = map.attributes as? MutableMap<MachineAttributeType, MachineAttributeInstance>
                        ?: error("Unsupported attributeMap implementation: ${map::class.qualifiedName}")
                    attrs.getOrPut(type) { MachineAttributeInstanceImpl(type, base = 1.0) }
                }
            }
        }

        private fun wrapModifierWithId(mod: MachineAttributeModifier, id: String): MachineAttributeModifier {
            return object : MachineAttributeModifier {
                override val id: String = id
                override val amount: Double = mod.amount
                override val operation: MachineAttributeModifier.Operation = mod.operation
                override val adder: Any? = mod.adder
                override fun apply(base: Double, current: Double): Double = mod.apply(base, current)
            }
        }
    }
}
