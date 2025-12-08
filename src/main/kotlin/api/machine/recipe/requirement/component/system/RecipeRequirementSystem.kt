package github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.RecipeRequirementComponent

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
 * - **check()**: Validate whether the requirement can start (e.g., resources available)
 * - **onStart()**: Perform initial consumption or reservation
 * - **onEnd()**: Finalize outputs or cleanup
 *
 * 生命周期钩子:
 * - **check()**: 验证需求是否可以开始（例如资源是否可用）
 * - **onStart()**: 执行初始消耗或预留
 * - **onEnd()**: 完成输出或清理
 *
 * ## Tickable Requirements / 可 Tick 的需求
 * For long-running recipes, requirements can be processed every tick via a transactional model
 * to ensure atomicity: acquireTickTransaction() -> commit()/rollback (rollback implied by not committing).
 *
 * 对于长时间运行的配方，需求可以通过事务模型在每个 tick 处理以确保原子性：
 * acquireTickTransaction() -> commit()/rollback（不提交即视为回滚）。
 *
 * @see RequirementTransaction
 */
public interface RecipeRequirementSystem<C : RecipeRequirementComponent> {

    /** Pre-start validation / 启动前验证 */
    public fun check(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    /** Initial consumption/reservation / 初始消耗或预留 */
    public fun onStart(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    /** Finalization/cleanup / 完成或清理 */
    public fun onEnd(machine: MachineInstance, component: C, process: RecipeProcess): ProcessResult

    /**
     * Tickable requirement extension. Provides transactional per-tick operations.
     * 可 Tick 需求扩展。提供事务性的每 tick 操作。
     */
    public interface Tickable<C : RecipeRequirementComponent> : RecipeRequirementSystem<C> {

        /**
         * Acquire a transaction for this tick. If result is Failure, do not commit.
         * 获取本 tick 的事务。如果结果为 Failure，则不提交。
         */
        public fun acquireTickTransaction(machine: MachineInstance, component: C, process: RecipeProcess): RequirementTransaction

    }

}

/**
 * # RequirementTransaction - Tick Transaction
 * # RequirementTransaction - Tick 事务
 *
 * Represents a two-phase operation for per-tick requirement processing.
 * Call commit() to finalize, or drop the transaction to rollback implicitly.
 *
 * 表示每 tick 需求处理的两阶段操作。
 * 调用 commit() 以完成，或丢弃事务以隐式回滚。
 */
public interface RequirementTransaction {

    /** Result of attempting to acquire the transaction / 获取事务的结果 */
    public val result: ProcessResult

    /** Finalize the transaction (apply state changes) / 完成事务（应用状态变更） */
    public fun commit()

}