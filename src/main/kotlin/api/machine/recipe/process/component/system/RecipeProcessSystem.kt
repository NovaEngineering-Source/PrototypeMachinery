package github.kasuminova.prototypemachinery.api.machine.recipe.process.component.system

import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.system.ComponentSystem

public interface RecipeProcessSystem<C : RecipeProcessComponent> : ComponentSystem<RecipeProcess, C> {

}