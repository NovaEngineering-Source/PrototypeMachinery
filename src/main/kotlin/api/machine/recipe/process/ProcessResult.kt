package github.kasuminova.prototypemachinery.api.machine.recipe.process

/**
 * # ProcessResult - Recipe Process Result
 * # ProcessResult - 配方进程结果
 * 
 * Sealed class representing the result of recipe processing operations.
 * Used throughout the recipe execution lifecycle for validation and error handling.
 * 
 * 表示配方处理操作结果的密封类。
 * 在整个配方执行生命周期中用于验证和错误处理。
 * 
 * ## Usage Contexts / 使用场景
 * 
 * - **check()**: Validate if recipe can start
 *   **check()**: 验证配方是否可以启动
 * 
 * - **onStart()**: Initial resource consumption
 *   **onStart()**: 初始资源消耗
 * 
 * - **onTick()**: Per-tick processing (via transaction)
 *   **onTick()**: 每 tick 处理（通过事务）
 * 
 * - **onEnd()**: Final output generation
 *   **onEnd()**: 最终输出生成
 * 
 * ## Error Handling / 错误处理
 * 
 * Failures include localized error messages for display to users:
 * - reason: Translation key for the error message
 * - args: Arguments for formatting the message
 * 
 * 失败包括用于显示给用户的本地化错误消息:
 * - reason: 错误消息的翻译键
 * - args: 格式化消息的参数
 * 
 * ## Related Classes / 相关类
 * 
 * - [github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem] - Returns ProcessResult
 * - [RecipeProcess] - Uses ProcessResult for status tracking
 * 
 * @see RecipeProcess
 */
public sealed class ProcessResult {

    /**
     * Operation succeeded.
     * 
     * 操作成功。
     */
    public object Success : ProcessResult()

    /**
     * Operation failed with a reason.
     * 
     * 操作失败并带有原因。
     * 
     * @param reason Translation key for error message / 错误消息的翻译键
     * @param args Formatting arguments / 格式化参数
     */
    public data class Failure(
        val reason: String,
        val args: List<String>,
    ) : ProcessResult()

}
