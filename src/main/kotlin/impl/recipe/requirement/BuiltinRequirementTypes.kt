package github.kasuminova.prototypemachinery.impl.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.AttributeModifierRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.ParallelismRequirementComponent

/**
 * Bootstrap holder for built-in requirement types that are not strictly ITEM/FLUID/ENERGY.
 *
 * 内建需求类型引导入口：放置“属性修改 / 并行度”等偏框架层的需求类型。
 */
public object BuiltinRequirementTypes {

    @JvmField
    @Deprecated("Use RecipeRequirementTypes.ATTRIBUTE_MODIFIER", ReplaceWith("RecipeRequirementTypes.ATTRIBUTE_MODIFIER"))
    public val ATTRIBUTE_MODIFIER: RecipeRequirementType<AttributeModifierRequirementComponent> =
        RecipeRequirementTypes.ATTRIBUTE_MODIFIER

    @JvmField
    @Deprecated("Use RecipeRequirementTypes.PARALLELISM", ReplaceWith("RecipeRequirementTypes.PARALLELISM"))
    public val PARALLELISM: RecipeRequirementType<ParallelismRequirementComponent> =
        RecipeRequirementTypes.PARALLELISM
}
