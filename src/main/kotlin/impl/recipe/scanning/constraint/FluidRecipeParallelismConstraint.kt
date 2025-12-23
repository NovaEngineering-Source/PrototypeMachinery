package github.kasuminova.prototypemachinery.impl.recipe.scanning.constraint

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureFluidKeyContainer
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.scanning.RecipeParallelismConstraint
import github.kasuminova.prototypemachinery.common.util.Action
import github.kasuminova.prototypemachinery.common.util.IOType
import github.kasuminova.prototypemachinery.common.util.RecipeParallelism
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack

public class FluidRecipeParallelismConstraint(
    override val requirementTypeId: ResourceLocation
) : RecipeParallelismConstraint {

    override fun canSatisfy(
        machine: MachineInstance,
        recipe: MachineRecipe,
        components: List<RecipeRequirementComponent>,
        parallels: Int
    ): Boolean {
        val cs = components.filterIsInstance<FluidRequirementComponent>()
        if (cs.isEmpty()) return true

        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureFluidKeyContainer::class.java)
            .filter { it.isAllowedIOType(IOType.OUTPUT) }

        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureFluidKeyContainer::class.java)
            .filter { it.isAllowedIOType(IOType.INPUT) }

        // 1) Inputs: start + perTick
        for (c in cs) {
            if (c.inputs.isNotEmpty() || c.inputsPerTick.isNotEmpty()) {
                if (sources.isEmpty()) return false

                if (!canExtractAll(sources, aggregateByKey(c.inputs, parallels))) return false
                if (!canExtractAll(sources, aggregateByKey(c.inputsPerTick, parallels))) return false
            }
        }

        // 2) Outputs: end + perTick, unless ignore_output_full
        for (c in cs) {
            val ignoreOutputFull = (c.properties["ignore_output_full"] as? Boolean) == true
            if (ignoreOutputFull) continue

            if (c.outputs.isNotEmpty() || c.outputsPerTick.isNotEmpty()) {
                if (targets.isEmpty()) return false

                if (!canInsertAll(targets, aggregateByKey(c.outputs, parallels))) return false
                if (!canInsertAll(targets, aggregateByKey(c.outputsPerTick, parallels))) return false
            }
        }

        return true
    }

    private fun aggregateByKey(keys: List<PMKey<FluidStack>>, parallels: Int): Map<PMKey<FluidStack>, Long> {
        if (keys.isEmpty()) return emptyMap()
        val map = LinkedHashMap<PMKey<FluidStack>, Long>()
        for (k in keys) {
            val required = RecipeParallelism.scaleCount(k.count, parallels)
            if (required <= 0L) continue
            map[k] = (map[k] ?: 0L) + required
        }
        return map
    }

    private fun canExtractAll(sources: List<StructureFluidKeyContainer>, requiredByKey: Map<PMKey<FluidStack>, Long>): Boolean {
        for ((key, totalRequired) in requiredByKey) {
            var remaining = totalRequired
            for (c in sources) {
                if (remaining <= 0L) break
                remaining -= c.extract(key, remaining, Action.SIMULATE)
            }
            if (remaining > 0L) return false
        }
        return true
    }

    private fun canInsertAll(targets: List<StructureFluidKeyContainer>, requiredByKey: Map<PMKey<FluidStack>, Long>): Boolean {
        for ((key, totalRequired) in requiredByKey) {
            var remaining = totalRequired
            for (c in targets) {
                if (remaining <= 0L) break
                remaining -= c.insert(key, remaining, Action.SIMULATE)
            }
            if (remaining > 0L) return false
        }
        return true
    }
}
