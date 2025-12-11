package github.kasuminova.prototypemachinery.api.recipe.requirement.component

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType

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