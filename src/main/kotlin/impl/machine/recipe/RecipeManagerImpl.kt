package github.kasuminova.prototypemachinery.impl.machine.recipe

import github.kasuminova.prototypemachinery.api.machine.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.machine.recipe.RecipeManager

// TODO Replace with registry.
public object RecipeManagerImpl : RecipeManager {
    private val recipes: MutableMap<String, MachineRecipe> = mutableMapOf()

    override fun get(id: String): MachineRecipe? = recipes[id]
    override fun getAll(): Collection<MachineRecipe> = recipes.values
    override fun register(recipe: MachineRecipe) {
        recipes[recipe.id] = recipe
    }
}
