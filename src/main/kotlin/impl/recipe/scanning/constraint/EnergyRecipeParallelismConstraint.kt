package github.kasuminova.prototypemachinery.impl.recipe.scanning.constraint

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.scanning.RecipeParallelismConstraint
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.RecipeParallelism
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureEnergyContainer
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import net.minecraft.util.ResourceLocation

public class EnergyRecipeParallelismConstraint(
    override val requirementTypeId: ResourceLocation
) : RecipeParallelismConstraint {

    override fun canSatisfy(
        machine: MachineInstance,
        recipe: MachineRecipe,
        components: List<RecipeRequirementComponent>,
        parallels: Int
    ): Boolean {
        val cs = components.filterIsInstance<EnergyRequirementComponent>()
        if (cs.isEmpty()) return true

        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureEnergyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.OUTPUT) }

        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureEnergyContainer::class.java)
            .filter { it.isAllowedPortMode(PortMode.INPUT) }

        // 1) Inputs: start + perTick
        for (c in cs) {
            val needStart = RecipeParallelism.scaleCount(c.input, parallels)
            val needTick = RecipeParallelism.scaleCount(c.inputPerTick, parallels)
            if (needStart > 0L || needTick > 0L) {
                if (sources.isEmpty()) return false
                if (needStart > 0L && !canExtractEnergy(sources, needStart)) return false
                if (needTick > 0L && !canExtractEnergy(sources, needTick)) return false
            }
        }

        // 2) Outputs: end + perTick, unless ignore_output_full
        for (c in cs) {
            val ignoreOutputFull = (c.properties["ignore_output_full"] as? Boolean) == true
            if (ignoreOutputFull) continue

            val outEnd = RecipeParallelism.scaleCount(c.output, parallels)
            val outTick = RecipeParallelism.scaleCount(c.outputPerTick, parallels)

            if (outEnd > 0L || outTick > 0L) {
                if (targets.isEmpty()) return false
                if (outEnd > 0L && !canInsertEnergy(targets, outEnd)) return false
                if (outTick > 0L && !canInsertEnergy(targets, outTick)) return false
            }
        }

        return true
    }

    private fun canExtractEnergy(sources: List<StructureEnergyContainer>, required: Long): Boolean {
        var remaining = required
        for (c in sources) {
            if (remaining <= 0L) break
            remaining -= c.extractEnergy(remaining, TransactionMode.SIMULATE)
        }
        return remaining <= 0L
    }

    private fun canInsertEnergy(targets: List<StructureEnergyContainer>, required: Long): Boolean {
        var remaining = required
        for (c in targets) {
            if (remaining <= 0L) break
            remaining -= c.insertEnergy(remaining, TransactionMode.SIMULATE)
        }
        return remaining <= 0L
    }
}
