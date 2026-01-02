package github.kasuminova.prototypemachinery.impl.recipe.index.type

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndex
import github.kasuminova.prototypemachinery.api.recipe.index.RequirementIndexFactory
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureFluidStorageContainerComponent
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
        val fluidComponents = machine.structureComponentMap.getByInstanceOf(
            github.kasuminova.prototypemachinery.api.machine.component.container.StructureFluidKeyContainer::class.java
        )

        if (fluidComponents.isEmpty()) {
            // No fluid containers, no opinion on recipes
            return null
        }

        var hasEnumerableSource = false
        var hasAnyKey = false
        val potentialRecipes = LinkedHashSet<MachineRecipe>()

        for (component in fluidComponents) {
            // Only consider OUTPUT mode containers as sources (fluids available for input)
            if (!component.isAllowedPortMode(PortMode.OUTPUT)) continue

            // Only storage-backed containers can be enumerated cheaply.
            val storageComponent = component as? StructureFluidStorageContainerComponent ?: continue
            hasEnumerableSource = true

            val storage = storageComponent.storage
            for (storedKey in storage.getAllResources()) {
                if (storedKey.count <= 0L) continue
                hasAnyKey = true

                // Count does not participate in equality for lookup.
                val lookupKey = storedKey.copy()
                lookupKey.count = 1L
                index[lookupKey]?.let { potentialRecipes.addAll(it) }
            }
        }

        // If we cannot enumerate any source container, indexing can't contribute.
        if (!hasEnumerableSource) return null

        // We enumerated successfully and found no keys => hard empty.
        if (!hasAnyKey) return emptySet()

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
                        // Index fuzzy candidates conservatively (they are explicit and thus safe to index).
                        @Suppress("UNCHECKED_CAST")
                        val fuzzy = req.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<FluidStack>>
                        if (!fuzzy.isNullOrEmpty()) {
                            for (group in fuzzy) {
                                for (cand in group.candidates) {
                                    map.computeIfAbsent(cand) { mutableSetOf() }.add(recipe)
                                }
                            }
                        }

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
