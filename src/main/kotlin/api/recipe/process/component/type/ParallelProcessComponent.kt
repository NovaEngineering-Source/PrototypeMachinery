package github.kasuminova.prototypemachinery.api.recipe.process.component.type

import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent

public interface ParallelProcessComponent : RecipeProcessComponent {

    public val parallelism: Int

}