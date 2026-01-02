package github.kasuminova.prototypemachinery.impl.recipe.requirement.component

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.IdentifiedRecipeRequirementComponent
import net.minecraft.util.ResourceLocation

/**
 * Applies a [MachineAttributeModifier] to the running [github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess].
 *
 * 这是一个“先打地基”的内建需求组件：用于对运行中的 RecipeProcess.attributeMap 施加/撤销属性修改器。
 *
 * 设计意图：
 * - 先支持最常用的“属性修改（速度/并行/效率等）”能力
 * - 等后续修饰符系统完善后，再把更复杂的数据驱动/脚本化逻辑接进来
 */
public data class AttributeModifierRequirementComponent(
    /** Stable id within the recipe (used to form deterministic modifier ids). */
    override val id: String,

    /** Attribute to modify (by id to avoid requiring a full attribute registry right now). */
    public val attributeId: ResourceLocation,

    /** The modifier to apply. Its id will be overridden to a deterministic internal id. */
    public val modifier: MachineAttributeModifier,

    /** Whether to apply at start(). / 是否在 start() 阶段施加 */
    public val applyAtStart: Boolean = true,

    /** Whether to remove at onEnd(). / 是否在 onEnd() 阶段撤销 */
    public val removeAtEnd: Boolean = true,
) : IdentifiedRecipeRequirementComponent {

    override val type: RecipeRequirementType<*> = RecipeRequirementTypes.ATTRIBUTE_MODIFIER
}
