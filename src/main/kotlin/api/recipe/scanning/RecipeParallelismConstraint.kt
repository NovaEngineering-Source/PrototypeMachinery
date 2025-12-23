package github.kasuminova.prototypemachinery.api.recipe.scanning

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import net.minecraft.util.ResourceLocation

/**
 * A pluggable constraint used by the recipe scanning system to decide whether a recipe can run with a given
 * effective parallel amount `k`.
 *
 * 用于“配方扫描阶段”计算最大可并行数 k 的可插拔约束。
 *
 * ### Design notes
 * - The scanning system will binary-search k in [1..limit].
 * - Each constraint decides whether the machine state can satisfy the requirement(s) at that k, typically via
 *   Action.SIMULATE.
 * - If a requirement type has no registered constraint, the scanner will conservatively clamp k to 1.
 */
public interface RecipeParallelismConstraint {

    /** Requirement type id this constraint applies to. / 约束对应的需求类型 ID */
    public val requirementTypeId: ResourceLocation

    /**
     * Optional fast upper bound estimation.
     *
     * You may return a value <= currentLimit to reduce the binary search space.
     *
     * 可选的上界估算：用于快速收敛二分范围。
     */
    public fun upperBound(
        machine: MachineInstance,
        recipe: MachineRecipe,
        components: List<RecipeRequirementComponent>,
        currentLimit: Int
    ): Int = currentLimit

    /**
     * Whether the machine can satisfy the given requirement components at the specified parallel amount.
     *
     * Return false to indicate the scanner should try a smaller k.
     */
    public fun canSatisfy(
        machine: MachineInstance,
        recipe: MachineRecipe,
        components: List<RecipeRequirementComponent>,
        parallels: Int
    ): Boolean
}
