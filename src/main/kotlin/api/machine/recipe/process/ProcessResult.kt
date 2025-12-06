package github.kasuminova.prototypemachinery.api.machine.recipe.process

public sealed class ProcessResult {
    public object Success : ProcessResult()
    public data class Failure(val reason: String) : ProcessResult()
}
