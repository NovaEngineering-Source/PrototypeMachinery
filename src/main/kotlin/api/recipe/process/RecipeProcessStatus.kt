package github.kasuminova.prototypemachinery.api.recipe.process

public data class RecipeProcessStatus(
    val progress: Float,
    val message: String = "",
    val isError: Boolean = false
)
