package github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeModifierImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.OverlayMachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent

/**
 * Minimal built-in system that writes process parallelism into process.attributeMap.
 *
 * 当前实现仅负责“把并行度这个数写入到进程属性里”。
 * 后续真正的并行执行逻辑会在执行器/修饰符系统完善后接入。
 */
public object ParallelismRequirementSystem : RecipeRequirementSystem<ParallelismRequirementComponent> {

    override fun start(process: RecipeProcess, component: ParallelismRequirementComponent): RequirementTransaction {
        if (!component.applyAtStart) return noOpSuccess()
        val target = component.parallelism.coerceAtLeast(1L)
        if (target == 1L) return noOpSuccess()

        val overlay = process.attributeMap as? OverlayMachineAttributeMapImpl ?: return noOpSuccess()
        val instance = overlay.getOrCreateAttribute(StandardMachineAttributes.PROCESS_PARALLELISM, defaultBase = 1.0)

        val modifierId = "req:${component.id}:process_parallelism"
        val amount = (target - 1L).toDouble()

        val applied = if (instance.hasModifier(modifierId)) {
            false
        } else {
            instance.addModifier(MachineAttributeModifierImpl.addition(modifierId, amount, adder = component))
        }

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                if (applied) {
                    instance.removeModifier(modifierId)
                }
            }
        }
    }

    override fun onEnd(process: RecipeProcess, component: ParallelismRequirementComponent): RequirementTransaction {
        if (!component.removeAtEnd) return noOpSuccess()

        val overlay = process.attributeMap as? OverlayMachineAttributeMapImpl ?: return noOpSuccess()
        val instance = overlay.getAttribute(StandardMachineAttributes.PROCESS_PARALLELISM) ?: return noOpSuccess()

        val modifierId = "req:${component.id}:process_parallelism"
        val removed = instance.removeModifier(modifierId)

        return object : RequirementTransaction {
            override val result: ProcessResult = ProcessResult.Success
            override fun commit() {}
            override fun rollback() {
                if (removed != null && !instance.hasModifier(modifierId)) {
                    instance.addModifier(removed)
                }
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
}
