package github.kasuminova.prototypemachinery.api.machine.recipe.process

import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent

public interface RecipeExecutor {

    public fun tick(processor: FactoryRecipeProcessorComponent)

}
