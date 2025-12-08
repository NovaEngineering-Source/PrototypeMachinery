package github.kasuminova.prototypemachinery.api.machine.recipe.requirement

import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.requirement.component.system.RecipeRequirementSystem
import net.minecraft.util.ResourceLocation

/**
 * # RecipeRequirementType - Requirement Type Descriptor
 * # RecipeRequirementType - 需求类型描述符
 *
 * Declares the category of a requirement (ITEM/FLUID/ENERGY/...) and binds it to
 * the system that can process the corresponding requirement components.
 *
 * 声明需求的类别（物品/流体/能量/...），并将其绑定到能够处理对应需求组件的系统。
 *
 * ## Examples / 示例
 * - ITEM -> ItemRequirementSystem
 * - FLUID -> FluidRequirementSystem
 * - ENERGY -> EnergyRequirementSystem
 *
 * ## Related Classes / 相关类
 * - [RecipeRequirement] - Requirement instances referencing this type
 * - [RecipeRequirementComponent] - Component bound to a machine to satisfy this type
 * - [RecipeRequirementSystem] - System that executes requirements of this type
 */
public interface RecipeRequirementType<C : RecipeRequirementComponent> {

    /** Unique ID for this requirement type / 需求类型的唯一 ID */
    public val id: ResourceLocation

    /** System responsible for processing this requirement type / 处理此需求类型的系统 */
    public val system: RecipeRequirementSystem<C>

}