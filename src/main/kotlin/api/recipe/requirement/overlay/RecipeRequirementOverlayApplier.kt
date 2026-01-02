package github.kasuminova.prototypemachinery.api.recipe.requirement.overlay

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import net.minecraft.nbt.NBTTagCompound

/**
 * # RecipeRequirementOverlayApplier
 * # 需求 Overlay 应用器
 *
 * Applies per-process overlay data to a concrete [RecipeRequirementComponent].
 *
 * 将 per-process overlay 数据应用到某个具体的需求组件上。
 */
public interface RecipeRequirementOverlayApplier<C : RecipeRequirementComponent> {

    /** The requirement type this applier targets. / 此应用器对应的需求类型 */
    public val type: RecipeRequirementType<C>

    /**
     * Apply overlay data and return an effective component.
     * 应用 overlay 数据并返回“生效的”组件（通常是 copy 后的新实例）。
     */
    public fun apply(component: C, data: NBTTagCompound): C
}
