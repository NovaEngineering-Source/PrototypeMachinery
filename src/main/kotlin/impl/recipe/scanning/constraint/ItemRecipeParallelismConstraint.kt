package github.kasuminova.prototypemachinery.impl.recipe.scanning.constraint

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureItemKeyContainer
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.scanning.RecipeParallelismConstraint
import github.kasuminova.prototypemachinery.common.util.Action
import github.kasuminova.prototypemachinery.common.util.IOType
import github.kasuminova.prototypemachinery.common.util.RecipeParallelism
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation

public class ItemRecipeParallelismConstraint(
    override val requirementTypeId: ResourceLocation
) : RecipeParallelismConstraint {

    override fun canSatisfy(
        machine: MachineInstance,
        recipe: MachineRecipe,
        components: List<RecipeRequirementComponent>,
        parallels: Int
    ): Boolean {
        val cs = components.filterIsInstance<ItemRequirementComponent>()
        if (cs.isEmpty()) return true

        val sources = machine.structureComponentMap
            .getByInstanceOf(StructureItemKeyContainer::class.java)
            .filter { it.isAllowedIOType(IOType.OUTPUT) }

        val targets = machine.structureComponentMap
            .getByInstanceOf(StructureItemKeyContainer::class.java)
            .filter { it.isAllowedIOType(IOType.INPUT) }

        // 1) Inputs (start): must be satisfiable
        for (c in cs) {
            if (c.inputs.isNotEmpty()) {
                if (sources.isEmpty()) return false

                // Aggregate counts by key to avoid over-estimation when the same key appears multiple times.
                val needed: Map<PMKey<ItemStack>, Long> = aggregate(c.inputs, parallels)

                for ((key, required) in needed) {
                    if (required <= 0L) continue
                    var remaining = required

                    for (container in sources) {
                        if (remaining <= 0L) break
                        remaining -= container.extract(key, remaining, Action.SIMULATE)
                    }

                    if (remaining > 0L) return false
                }
            }
        }

        // 2) Outputs (end): check capacity unless ignore_output_full
        for (c in cs) {
            if (c.outputs.isEmpty()) continue

            val ignoreOutputFull = (c.properties["ignore_output_full"] as? Boolean) == true
            if (ignoreOutputFull) continue

            if (targets.isEmpty()) return false

            for (out in c.outputs) {
                val total = RecipeParallelism.scaleCount(out.count, parallels)
                if (total <= 0L) continue
                var remainingCount = total

                for (container in targets) {
                    if (remainingCount <= 0L) break
                    remainingCount -= container.insert(out, remainingCount, Action.SIMULATE)
                }

                if (remainingCount > 0L) return false
            }
        }

        return true
    }

    private fun aggregate(keys: List<PMKey<ItemStack>>, parallels: Int): Map<PMKey<ItemStack>, Long> {
        val map = LinkedHashMap<PMKey<ItemStack>, Long>()
        for (k in keys) {
            val c = RecipeParallelism.scaleCount(k.count, parallels)
            if (c <= 0L) continue
            map[k] = (map[k] ?: 0L) + c
        }
        return map
    }

}
