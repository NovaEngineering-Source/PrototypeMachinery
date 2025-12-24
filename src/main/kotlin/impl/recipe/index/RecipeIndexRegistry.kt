package github.kasuminova.prototypemachinery.impl.recipe.index

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.IRecipeIndexRegistry
import github.kasuminova.prototypemachinery.api.recipe.index.RecipeIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.impl.recipe.index.type.EnergyRequirementIndex
import github.kasuminova.prototypemachinery.impl.recipe.index.type.FluidRequirementIndex
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
        registerFactory(FluidRequirementIndex.Factory)
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

    /**
     * Initialize the registry for testing purposes.
     * This should be called in test setup before any scanning system tests run.
     *
     * 用于测试目的初始化注册表。
     * 应在测试设置中调用，在任何扫描系统测试运行之前。
     */
    internal fun initializeForTests() {
        // No-op for now, as the init block already sets INSTANCE.
    }

    private fun isEligibleForIndexing(machineType: MachineType): Boolean {
        // Check if any of the machine's recipes have dynamic modifiers.
        // If a recipe uses dynamic modifiers (e.g., DynamicItemInputGroup),
        // the machine should not be indexed since the actual requirements
        // can only be determined at runtime.

        // For now, we return true to enable indexing by default.
        // A more complete implementation would:
        // 1. Check all recipes for this machine type
        // 2. Look for dynamic modifiers in their requirements
        // 3. Return false if any dynamic modifiers are found

        // Example of what to check for (pseudo-code):
        // for (recipe in getRecipesForMachineType(machineType)) {
        //     for (requirement in recipe.requirements.values.flatten()) {
        //         if (requirement.hasDynamicModifiers()) return false
        //     }
        // }

        return true
    }

}