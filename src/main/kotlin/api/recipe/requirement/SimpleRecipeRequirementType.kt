package github.kasuminova.prototypemachinery.api.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import net.minecraft.util.ResourceLocation

/**
 * A minimal [RecipeRequirementType] implementation.
 *
 * 用于降低第三方模组自定义需求类型的样板代码：
 * 只需提供 id + system 即可创建并注册新的需求类型。
 */
public data class SimpleRecipeRequirementType<C : RecipeRequirementComponent>(
    override val id: ResourceLocation,
    override val system: RecipeRequirementSystem<C>,
) : RecipeRequirementType<C>
