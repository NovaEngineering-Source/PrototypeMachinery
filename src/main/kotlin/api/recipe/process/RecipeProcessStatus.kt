package github.kasuminova.prototypemachinery.api.recipe.process

/**
 * # RecipeProcessStatus - Lightweight status for UI
 * # RecipeProcessStatus - 用于 UI 的轻量状态
 *
 * A small immutable snapshot for exposing process status to the UI/network layer.
 *
 * 用于向 UI/网络层暴露进程状态的小型不可变快照。
 */
public data class RecipeProcessStatus(
    val progress: Float,
    val message: String = "",
    val isError: Boolean = false
)
