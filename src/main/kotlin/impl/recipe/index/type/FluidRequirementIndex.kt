package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import net.minecraftforge.fluids.FluidStack

/**
 * # FluidRequirementIndex
 * # 流体需求索引
 *
 * Index for fluid-based recipe requirements.
 * Maps fluid types to recipes that require them.
 *
 * 基于流体的配方需求索引。
 * 将流体类型映射到需要它们的配方。
 */
public class FluidRequirementIndex(
    private val index: Map<PMKey<FluidStack>, Set<MachineRecipe>>
) : RequirementIndex {

    public override fun lookup(machine: MachineInstance): Set<MachineRecipe>? {
        // 1. Get all StructureFluidKeyContainers from the machine
        // We use StructureFluidKeyContainer (key-level API) for consistency with ItemRequirementIndex
        val fluidComponents = machine.structureComponentMap.getByInstanceOf(
            github.kasuminova.prototypemachinery.api.machine.component.container.StructureFluidKeyContainer::class.java
        )

        if (fluidComponents.isEmpty()) {
            // No fluid containers, no opinion on recipes
            return null
        }

        // 2. Collect all available fluids as PMKey<FluidStack>
        // We're only interested in type (prototype), not count
        // because the index is a conservative filter
        val availableKeys = mutableSetOf<PMKey<FluidStack>>()

        for (component in fluidComponents) {
            // Only consider OUTPUT mode containers as sources (fluids available for input)
            if (!component.isAllowedPortMode(github.kasuminova.prototypemachinery.api.util.PortMode.OUTPUT)) {
                continue
            }

            // Try to access storage if this is a storage-backed component
            // (e.g., StructureFluidStorageContainerComponent)
            if (component is github.kasuminova.prototypemachinery.impl.machine.component.container.StructureFluidStorageContainerComponent) {
                val storage = component.storage

                // Get all stored fluid types
                for (storedKey in storage.getAllResources()) {
                    if (storedKey.count > 0L) {
                        // Create a copy with count=1 for lookup
                        // (count doesn't matter for equality)
                        val lookupKey = storedKey.copy()
                        lookupKey.count = 1L
                        availableKeys.add(lookupKey)
                    }
                }
            }
        }

        if (availableKeys.isEmpty()) {
            // No fluids available, no recipes can match
            return emptySet()
        }

        // 3. Query the index
        // Union of all recipes that match ANY available fluid
        // This is conservative: it may include recipes that need other inputs,
        // but won't exclude recipes that could potentially match
        val potentialRecipes = mutableSetOf<MachineRecipe>()

        for (key in availableKeys) {
            val recipes = index[key]
            if (recipes != null) {
                potentialRecipes.addAll(recipes)
            }
        }

        return if (potentialRecipes.isEmpty()) emptySet() else potentialRecipes
    }

    public companion object Factory : RequirementIndexFactory {
        override val requirementType: RecipeRequirementType<*> = RecipeRequirementTypes.FLUID

        public override fun create(machineType: MachineType, recipes: List<MachineRecipe>): RequirementIndex? {
            val map = mutableMapOf<PMKey<FluidStack>, MutableSet<MachineRecipe>>()

            for (recipe in recipes) {
                // Get all FluidRequirementComponents from this recipe
                val fluidReqs = recipe.requirements[RecipeRequirementTypes.FLUID]
                if (fluidReqs.isNullOrEmpty()) continue

                for (req in fluidReqs) {
                    if (req is github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent) {
                        // Index each input fluid type
                        for (inputKey in req.inputs) {
                            // The key is already a PMKey<FluidStack>
                            map.computeIfAbsent(inputKey) { mutableSetOf() }.add(recipe)
                        }

                        // Also index per-tick inputs
                        for (inputKey in req.inputsPerTick) {
                            map.computeIfAbsent(inputKey) { mutableSetOf() }.add(recipe)
                        }
                    }
                }
            }

            if (map.isEmpty()) return null

            return FluidRequirementIndex(map)
        }
    }
}
