package github.kasuminova.prototypemachinery.api.recipe.requirement.component.system

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
 * - **start()**: 验证并执行初始消耗或预留（事务性）
 * - **onEnd()**: 完成输出或清理（事务性）
 *
 * ## Tickable Requirements / 可 Tick 的需求
 * For long-running recipes, requirements can be processed every tick via a transactional model
 * to ensure atomicity: acquireTickTransaction() -> commit() / rollback().
 *
 * Operations are applied immediately upon acquisition.
 *
 * **Caller contract**:
 * - If result is **Failure**: call rollback(); commit() must NOT be called.
 * - Otherwise (Success/Blocked): call commit(); rollback() must NOT be called.
 *
 * commit() is required to be successful; if an implementation cannot commit, it MUST throw an exception
 * (this indicates a programmer error / inconsistent state).
 * rollback() MUST NOT trigger any commit() of any transaction.
 *
 * 对于长时间运行的配方，需求可以通过事务模型在每个 tick 处理以确保原子性：
 * acquireTickTransaction() -> commit() / rollback()。
 *
 * 操作在获取事务时立即应用。
 *
 * **调用方约定**：
 * - 当 result 为 **Failure**：调用 rollback()；绝对不会调用 commit()。
 * - 其他情况（Success/Blocked）：调用 commit()；绝对不会调用 rollback()。
 *
 * commit() 必须保证成功；若实现无法提交，必须抛出异常（代表实现/调用存在逻辑错误或状态不一致）。
 * rollback() 触发时绝对不会触发任意事务的 commit()。
 *
 * ### Example: Catalyst / 示例：催化剂
 * - acquire transaction: consume / produce catalyst item(s) immediately
 * - commit(): apply special effect (e.g. reduce recipe working time)
 * - rollback(): return consumed item(s) back to the inventory
 * - 获取事务：立即消耗催化剂物品
 * - commit()：应用特殊效果（例如：减少配方工作时间）
 * - rollback()：将消耗的物品返还至库存
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
     * @return A transaction representing the start operation.
     *         - Call commit() when the transaction is accepted.
     *         - Call rollback() if the recipe cannot start or the overall process aborts.
     *
     *         返回表示开始操作的事务。
     *         - 当事务被确认采用时调用 commit()。
     *         - 若配方无法开始或整体流程中止，调用 rollback()。
     */
    public fun start(process: RecipeProcess, component: C): RequirementTransaction

    /** Finalization/cleanup / 完成或清理 */
    public fun onEnd(process: RecipeProcess, component: C): RequirementTransaction

    /**
     * Tickable requirement extension. Provides transactional per-tick operations.
     * 可 Tick 需求扩展。提供事务性的每 tick 操作。
     */
    public interface Tickable<C : RecipeRequirementComponent> : RecipeRequirementSystem<C> {

        /**
         * Acquire a transaction for this tick. Operations are applied immediately.
         * - Call commit() when the tick transaction is accepted.
         * - Call rollback() if the tick is rejected/aborted and changes must be reverted.
         *
         * 获取本 tick 的事务。操作立即应用。
         * - 当本 tick 的事务被确认采用时调用 commit()。
         * - 若本 tick 被拒绝/中止需要撤销变更时调用 rollback()。
         */
        public fun acquireTickTransaction(process: RecipeProcess, component: C): RequirementTransaction

    }

}

/**
 * # RequirementTransaction - 需求事务
 *
 * Represents a transactional operation result.
 *
 * **Important semantics**:
 * - The core operation is applied immediately when the transaction is acquired/created.
 * - commit() is the success-path finalization step and is required to succeed.
 * - rollback() is only called when result is Failure, and must revert **all** changes introduced by acquisition
 *   (and any internal side effects).
 * - rollback() MUST NOT trigger any commit() of any transaction.
 *
 * 表示事务性操作结果。
 *
 * **关键语义**：
 * - 事务的“核心操作”在获取/创建事务时就已经立即生效。
 * - commit() 是成功路径的结算步骤，并且必须保证成功。
 * - rollback() 仅在 result 为 Failure 时才会被调用，必须撤销获取阶段引入的**全部**变更（以及内部副作用）。
 * - rollback() 触发时绝对不会触发任意事务的 commit()。
 */
public interface RequirementTransaction {

    /**
     * A shared no-op success transaction instance.
     *
     * 通用的“成功且无副作用”的事务单例，用于避免在各处反复构造匿名对象。
     *
     * - result = [ProcessResult.Success]
     * - commit()/rollback() 均为 no-op
     */
    public object NoOpSuccess : RequirementTransaction {
        override val result: ProcessResult = ProcessResult.Success
        override fun commit() {}
        override fun rollback() {}
    }

    /** Result of attempting to acquire the transaction / 获取事务的结果 */
    public val result: ProcessResult

    /**
     * Finalize the transaction / 提交（结算）事务。
     *
     * Called when the transaction is accepted.
     * Useful for post-acquire effects (e.g., catalysts applying a speed modifier after being consumed).
     *
     * 当事务被确认采用时调用。
     * 适用于“获取后结算”的效果（例如：催化剂在已消耗后应用速度/时长修正）。
     */
    public fun commit()

    /** Rollback the transaction (undo state changes) / 回滚事务（撤销状态变更） */
    public fun rollback()

}