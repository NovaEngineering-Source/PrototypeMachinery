package github.kasuminova.prototypemachinery.api.recipe.requirement.component

/**
 * # IdentifiedRecipeRequirementComponent
 * # 带稳定 ID 的需求组件
 *
 * A [RecipeRequirementComponent] that has a stable identifier within a recipe.
 *
 * This is used by features that need to address a specific requirement component instance,
 * such as per-process overlays.
 *
 * 拥有配方内稳定 id 的需求组件。
 * 用于需要“精确定位某个需求组件实例”的功能，例如 per-process overlay。
 */
public interface IdentifiedRecipeRequirementComponent : RecipeRequirementComponent {

    /** Stable id within the recipe. / 配方内稳定 id */
    public val id: String
}
