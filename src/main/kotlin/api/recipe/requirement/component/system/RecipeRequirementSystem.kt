package github.kasuminova.prototypemachinery.api.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent

/**
 * # RecipeRequirementSystem - Requirement Processing System
 * # RecipeRequirementSystem - 需求处理系统
 *
 * Executes recipe requirements for a specific requirement type. Each requirement type has
 * one corresponding system that handles validation, consumption, and completion logic.
 *
 * 为特定需求类型执行配方需求。每个需求类型都有一个对应的系统，负责验证、消耗和完成逻辑。
 *
 * ## Lifecycle Hooks / 生命周期钩子
 * - **start()**: Validate and perform initial consumption/reservation (Transactional)
 * - **onEnd()**: Finalize outputs or cleanup (Transactional)
 *
 * 生命周期钩子:
 * - **start()**: 验证并执行初始消耗或预留（事务性）
 * - **onEnd()**: 完成输出或清理（事务性）
 *
 * ## Tickable Requirements / 可 Tick 的需求
 * For long-running recipes, requirements can be processed every tick via a transactional model
 * to ensure atomicity: acquireTickTransaction() -> rollback() (if failed).
 * Operations are applied immediately upon acquisition; call rollback to revert if the overall process fails.
 *
 * 对于长时间运行的配方，需求可以通过事务模型在每个 tick 处理以确保原子性：
 * acquireTickTransaction() -> rollback()（如果失败）。
 * 操作在获取事务时立即应用；如果整体流程失败，调用 rollback 以恢复。
 *
 * @see RequirementTransaction
 */
public interface RecipeRequirementSystem<C : RecipeRequirementComponent> {

    /**
     * Start the requirement processing.
     * Validates conditions and performs initial consumption/reservation.
     *
     * 开始需求处理。
     * 验证条件并执行初始消耗/预留。
     *
     * @return A transaction representing the start operation. Call rollback() if the recipe cannot start.
     *         返回表示开始操作的事务。如果配方无法开始，调用 rollback()。
     */
    public fun start(machine: MachineInstance, component: C, process: RecipeProcess): RequirementTransaction

    /** Finalization/cleanup / 完成或清理 */
    public fun onEnd(machine: MachineInstance, component: C, process: RecipeProcess): RequirementTransaction

    /**
     * Tickable requirement extension. Provides transactional per-tick operations.
     * 可 Tick 需求扩展。提供事务性的每 tick 操作。
     */
    public interface Tickable<C : RecipeRequirementComponent> : RecipeRequirementSystem<C> {

        /**
         * Acquire a transaction for this tick. Operations are applied immediately.
         * 获取本 tick 的事务。操作立即应用。
         */
        public fun acquireTickTransaction(machine: MachineInstance, component: C, process: RecipeProcess): RequirementTransaction

    }

}

/**
 * # RequirementTransaction - 需求事务
 *
 * Represents a transactional operation result.
 * The operation is applied when the transaction is created.
 * Call rollback() to undo changes if the overall process fails or is aborted.
 *
 * 表示事务性操作结果。
 * 操作在事务创建时已应用。
 * 如果整体流程失败或中止，调用 rollback() 以撤销更改。
 */
public interface RequirementTransaction {

    /** Result of attempting to acquire the transaction / 获取事务的结果 */
    public val result: ProcessResult

    /** Rollback the transaction (undo state changes) / 回滚事务（撤销状态变更） */
    public fun rollback()

}