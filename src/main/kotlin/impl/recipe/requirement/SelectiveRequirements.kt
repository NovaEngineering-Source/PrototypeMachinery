package github.kasuminova.prototypemachinery.impl.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.SelectiveRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.SelectiveRequirementType
import org.jetbrains.annotations.ApiStatus

/**
 * Bootstrap holder for Selective requirement type.
 * 选择性需求类型的引导入口。
 */
@ApiStatus.Experimental
public object SelectiveRequirements {

    @JvmField
    public val SELECTIVE_TYPE: RecipeRequirementType<SelectiveRequirementComponent> =
        RecipeRequirementTypes.register(SelectiveRequirementType())
}
