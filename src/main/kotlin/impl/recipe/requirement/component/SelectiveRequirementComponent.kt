package github.kasuminova.prototypemachinery.impl.recipe.requirement.component

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.SelectiveRequirements
import org.jetbrains.annotations.ApiStatus

/**
 * # SelectiveRequirementComponent
 * # 选择性需求组件
 *
 * A wrapper component that tries to pick **one** requirement from [candidates].
 * Once selected, the same candidate will be used for the rest of the process.
 *
 * 这是一个“选择性包装”组件：会从 [candidates] 中选择 **一个** 需求。
 * 一旦选定，将在后续整个配方过程中只使用该候选。
 *
 * ## Why "selective" (not "catalyst")?
 * ## 为什么叫“选择性需求”（而不是“催化剂需求”）？
 *
 * This wrapper is fundamentally about selecting one of multiple alternatives.
 * Naming it as "catalyst" causes ambiguity when used to wrap output requirements.
 *
 * 本质上它解决的是“从多个候选中择一”的问题。
 * 若称为“催化剂”，在用于包装输出需求时容易产生语义歧义，因此统一更正为“选择性需求”。
 */
@ApiStatus.Experimental
public data class SelectiveRequirementComponent(
    /** Stable identifier within a recipe (used for per-process state). / 配方内的稳定标识（用于进程状态）。 */
    public val id: String,
    /** Candidate requirement components to try in order. / 依次尝试的候选需求列表。 */
    public val candidates: List<RecipeRequirementComponent>,
    /**
     * Optional modifier ids invoked when the selection is committed.
     * 选择被 commit 后可选触发的修改器 id 列表。
     */
    public val modifierIds: List<String> = emptyList(),
) : RecipeRequirementComponent {

    override val type: RecipeRequirementType<*> = SelectiveRequirements.SELECTIVE_TYPE
}
