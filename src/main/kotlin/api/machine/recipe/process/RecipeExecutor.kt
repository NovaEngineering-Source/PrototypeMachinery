package github.kasuminova.prototypemachinery.api.machine.recipe.process

import github.kasuminova.prototypemachinery.api.machine.component.type.RecipeProcessorComponent

public interface RecipeExecutor {

    public fun tick(processor: RecipeProcessorComponent)

}
