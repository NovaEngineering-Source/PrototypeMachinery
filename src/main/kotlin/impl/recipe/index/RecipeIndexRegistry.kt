package github.kasuminova.prototypemachinery.impl.recipe.index

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.IRecipeIndexRegistry
import github.kasuminova.prototypemachinery.api.recipe.index.RecipeIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.impl.recipe.index.type.EnergyRequirementIndex
import github.kasuminova.prototypemachinery.impl.recipe.index.type.ItemRequirementIndex
import java.util.concurrent.ConcurrentHashMap

/**
 * # Recipe Index Registry Implementation
 * # 配方索引注册表实现
 *
 * Implementation of [IRecipeIndexRegistry].
 *
 * [IRecipeIndexRegistry] 的实现。
 */
public object RecipeIndexRegistry : IRecipeIndexRegistry {

    private val factories = mutableListOf<RequirementIndexFactory>()
    private val indices = ConcurrentHashMap<MachineType, RecipeIndex>()

    init {
        IRecipeIndexRegistry.INSTANCE = this
        registerFactory(ItemRequirementIndex.Factory)
        registerFactory(EnergyRequirementIndex.Factory)
    }

    public override fun registerFactory(factory: RequirementIndexFactory) {
        factories.add(factory)
    }

    public override fun getIndex(machineType: MachineType): RecipeIndex? = indices[machineType]

    /**
     * Called in PostInit to build indices for all machines.
     *
     * 在 PostInit 阶段调用，用于为所有机器构建索引。
     */
    public fun buildIndices(machineTypes: Collection<MachineType>, recipeLookup: (MachineType) -> List<MachineRecipe>) {
        for (type in machineTypes) {
            // Check if machine is eligible for indexing (no dynamic modifiers)
            if (!isEligibleForIndexing(type)) {
                continue
            }

            val recipes = recipeLookup(type)
            if (recipes.isEmpty()) continue

            val typeIndices = mutableListOf<github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex>()

            for (factory in factories) {
                val index = factory.create(type, recipes)
                if (index != null) {
                    typeIndices.add(index)
                }
            }

            if (typeIndices.isNotEmpty()) {
                indices[type] = RecipeIndex(typeIndices)
            }
        }
    }

    private fun isEligibleForIndexing(machineType: MachineType): Boolean {
        // TODO: Check all requirement components of the machine.
        // If any component supports dynamic modification of inputs/outputs (e.g. via CraftTweaker scripts that change logic at runtime, 
        // or specific upgrades that change input types), return false.
        // For now, we assume true or check a flag on the component type.
        /*
        for (componentType in machineType.componentTypes) {
             if (componentType.isDynamic) return false
        }
        */
        return true
    }

}