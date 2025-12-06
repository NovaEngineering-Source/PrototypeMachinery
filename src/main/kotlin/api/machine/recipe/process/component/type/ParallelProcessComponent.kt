package github.kasuminova.prototypemachinery.api.machine.recipe.process.component.type

import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponent

public interface ParallelProcessComponent : RecipeProcessComponent {

    public val parallelism: Int

}
