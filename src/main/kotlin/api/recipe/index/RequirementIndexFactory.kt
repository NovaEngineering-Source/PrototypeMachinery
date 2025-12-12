package github.kasuminova.prototypemachinery.api.recipe.index

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType

/**
 * # Requirement Index Factory
 * # 需求索引工厂
 *
 * Factory interface for creating requirement indices.
 *
 * 用于创建需求索引的工厂接口。
 */
public interface RequirementIndexFactory {
    /**
     * The type of requirement this index handles.
     *
     * 此索引处理的需求类型。
     */
    public val requirementType: RecipeRequirementType<*>

    /**
     * Tries to build an index for the given machine type and recipes.
     *
     * 尝试为给定的机器类型和配方构建索引。
     *
     * @return The index, or null if this factory cannot handle the machine's requirements or if indexing is not possible (e.g. dynamic modifiers).
     * 返回索引，如果此工厂无法处理该机器的需求或无法进行索引（例如存在动态修改器），则返回 null。
     */
    public fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex?
}