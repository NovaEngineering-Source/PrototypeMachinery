package github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent

/**
 * Minimal built-in system that writes process parallelism into process.attributeMap.
 *
 * 当前实现仅负责“把并行度这个数写入到进程属性里”。
 * 后续真正的并行执行逻辑会在执行器/修饰符系统完善后接入。
 */
public object ParallelismRequirementSystem : RecipeRequirementSystem<ParallelismRequirementComponent> {

    override fun start(process: RecipeProcess, component: ParallelismRequirementComponent): RequirementTransaction {
        // NOTE:
        // As of the parallelism overhaul, the *effective* parallel amount is selected by the recipe scanning system
        // (FactoryRecipeScanningSystem) and stored into process.attributeMap(PROCESS_PARALLELISM).base.
        //
        // Keeping this requirement type for data compatibility, but it no longer mutates the attribute map here,
        // otherwise we'd double-apply parallelism and break scaling.
        return noOpSuccess()
    }

    override fun onEnd(process: RecipeProcess, component: ParallelismRequirementComponent): RequirementTransaction {
        return noOpSuccess()
    }

    private fun noOpSuccess(): RequirementTransaction {
        return RequirementTransaction.NoOpSuccess
    }
}
