package github.kasuminova.prototypemachinery.api.machine.recipe.process

public data class RecipeProcessStatus(
    val progress: Float,
    val message: String = "",
    val isError: Boolean = false
)
