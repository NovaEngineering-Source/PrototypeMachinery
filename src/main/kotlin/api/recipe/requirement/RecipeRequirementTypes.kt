package github.kasuminova.prototypemachinery.api.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.AttributeModifierRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.EnergyRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.FluidRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.ItemRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.ParallelismRequirementType
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

public object RecipeRequirementTypes {

    private val types: MutableMap<ResourceLocation, RecipeRequirementType<*>> = ConcurrentHashMap()

    @JvmField
    public val ITEM: ItemRequirementType = register(ItemRequirementType())

    @JvmField
    public val FLUID: FluidRequirementType = register(FluidRequirementType())

    @JvmField
    public val ENERGY: EnergyRequirementType = register(EnergyRequirementType())

    /**
     * Built-in process modifier requirements.
     * 内建进程修改类需求（用于并行度、速度等基础能力）。
     */
    @JvmField
    public val ATTRIBUTE_MODIFIER: AttributeModifierRequirementType = register(AttributeModifierRequirementType())

    @JvmField
    public val PARALLELISM: ParallelismRequirementType = register(ParallelismRequirementType())

    @JvmStatic
    public fun <C : RecipeRequirementComponent, T : RecipeRequirementType<C>> register(type: T): T {
        types[type.id] = type
        return type
    }

    @JvmStatic
    public fun get(id: ResourceLocation): RecipeRequirementType<*>? {
        return types[id]
    }

    @JvmStatic
    public fun all(): Collection<RecipeRequirementType<*>> {
        return types.values
    }

}