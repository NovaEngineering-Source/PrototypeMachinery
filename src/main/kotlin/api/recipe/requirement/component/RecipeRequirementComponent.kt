package github.kasuminova.prototypemachinery.api.recipe.requirement.component

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType

/**
 * # RecipeRequirementComponent - Requirement component payload
 * # RecipeRequirementComponent - 需求组件负载
 *
 * A typed payload attached to a [github.kasuminova.prototypemachinery.api.recipe.MachineRecipe]
 * (grouped by [github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType]).
 * It is interpreted/executed by the matching requirement system.
 *
 * 绑定到配方（MachineRecipe）上的“带类型负载”（按 RecipeRequirementType 分组）。
 * 由对应的 requirement system 解释与执行。
 */
public interface RecipeRequirementComponent {

    public val type: RecipeRequirementType<*>

    /**
     * Custom properties for this component.
     * 此组件的自定义属性。
     *
     * Can be used to store flags or configuration like "ignore_output_full".
     * 可用于存储标志或配置，如“忽略输出已满”。
     */
    public val properties: Map<String, Any>
        get() = emptyMap()

}