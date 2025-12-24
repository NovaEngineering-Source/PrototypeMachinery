package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.FluidRequirementJeiRenderer
import net.minecraftforge.fluids.FluidStack

public object VanillaFluidNodeIngredientProvider : PMJeiNodeIngredientProvider<FluidRequirementComponent, FluidStack> {

    override val type: github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType<FluidRequirementComponent> = RecipeRequirementTypes.FLUID

    override val kind: JeiSlotKind = JeiSlotKinds.FLUID

    override fun getDisplayed(ctx: JeiRecipeContext, node: PMJeiRequirementNode<FluidRequirementComponent>): List<FluidStack> {
        val id = node.nodeId
        return when {
            id.contains(":fuzzy_input:") -> getFuzzyDisplayed(node)
            id.contains(":random_output:") -> getRandomOutputDisplayed(node)
            else -> FluidRequirementJeiRenderer.getDisplayedFluids(node)
        }
    }

    private data class ParsedAltNode(val groupOrPick: Int, val candidate: Int?)

    private fun parseAltNodeId(nodeId: String, marker: String): ParsedAltNode? {
        val pos = nodeId.indexOf(marker)
        if (pos < 0) return null
        val tail = nodeId.substring(pos + marker.length)
        val parts = tail.split(':')
        val groupOrPick = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val candidate = parts.getOrNull(1)?.toIntOrNull()
        return ParsedAltNode(groupOrPick = groupOrPick, candidate = candidate)
    }

    @Suppress("UNCHECKED_CAST")
    private fun getFuzzyDisplayed(node: PMJeiRequirementNode<FluidRequirementComponent>): List<FluidStack> {
        val comp = node.component
        val groups = comp.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<FluidStack>>
            ?: return emptyList()
        val parsed = parseAltNodeId(node.nodeId, ":fuzzy_input:")
        val groupIndex = parsed?.groupOrPick ?: node.index
        val group = groups.getOrNull(groupIndex) ?: return emptyList()
        if (group.candidates.isEmpty()) return emptyList()

        val desiredAmount = group.count.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val candidateIndex = parsed?.candidate
        val keys = if (candidateIndex != null) {
            listOfNotNull(group.candidates.getOrNull(candidateIndex))
        } else {
            group.candidates
        }

        return keys.mapNotNull { key ->
            val base = key.get()
            if (base.fluid == null) return@mapNotNull null
            FluidStack(base, desiredAmount)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRandomOutputDisplayed(node: PMJeiRequirementNode<FluidRequirementComponent>): List<FluidStack> {
        val comp = node.component
        val pool = comp.properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<FluidStack>
            ?: return emptyList()
        if (pool.candidates.isEmpty()) return emptyList()

        val parsed = parseAltNodeId(node.nodeId, ":random_output:")
        val candidateIndex = parsed?.candidate
        val list = if (candidateIndex != null) {
            listOfNotNull(pool.candidates.getOrNull(candidateIndex))
        } else {
            pool.candidates
        }

        return list.mapNotNull { wk ->
            val base = wk.key.get()
            if (base.fluid == null) return@mapNotNull null
            val desiredAmount = wk.key.count.coerceAtLeast(1L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            FluidStack(base, desiredAmount)
        }
    }
}
