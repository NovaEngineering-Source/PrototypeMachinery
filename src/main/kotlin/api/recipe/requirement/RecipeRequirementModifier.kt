package github.kasuminova.prototypemachinery.api.recipe.requirement

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess

/**
 * # RecipeRequirementModifier
 * # 配方需求修改器
 *
 * Allows dynamic modification of requirement data before execution.
 * 允许在执行前动态修改需求数据。
 *
 * This is useful for:
 * - Machine upgrades affecting consumption/output
 * - Randomization logic
 * - Conditional changes based on machine state
 *
 * 这对于以下情况很有用：
 * - 影响消耗/产出的机器升级
 * - 随机化逻辑
 * - 基于机器状态的条件变更
 */
public fun interface RecipeRequirementModifier<D : RecipeRequirementData> {

    /**
     * Modify the requirement data.
     * 修改需求数据。
     *
     * @param machine The machine instance executing the process. / 执行进程的机器实例。
     * @param process The current recipe process. / 当前配方进程。
     * @param originalData The original (or previously modified) data. / 原始（或已被修改的）数据。
     * @return The modified data to be used for execution. / 用于执行的修改后数据。
     */
    public fun modify(machine: MachineInstance, process: RecipeProcess, originalData: D): D

}
