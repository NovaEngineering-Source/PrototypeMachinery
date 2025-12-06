package github.kasuminova.prototypemachinery.api.machine.recipe

public interface RecipeManager {
    public fun get(id: String): MachineRecipe?
    public fun getAll(): Collection<MachineRecipe>
    public fun register(recipe: MachineRecipe)
}
