package github.kasuminova.prototypemachinery.api.recipe.requirement

import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.EnergyRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.FluidRequirementType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.type.ItemRequirementType
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