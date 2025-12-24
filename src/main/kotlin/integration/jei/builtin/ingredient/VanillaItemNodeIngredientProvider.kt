package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.DynamicItemInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.ingredient.PMJeiNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKind
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotKinds
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.ItemRequirementJeiRenderer
import net.minecraft.item.ItemStack

public object VanillaItemNodeIngredientProvider : PMJeiNodeIngredientProvider<ItemRequirementComponent, ItemStack> {

    override val type: github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType<ItemRequirementComponent> = RecipeRequirementTypes.ITEM

    override val kind: JeiSlotKind = JeiSlotKinds.ITEM

    override fun getDisplayed(ctx: JeiRecipeContext, node: PMJeiRequirementNode<ItemRequirementComponent>): List<ItemStack> {
        val id = node.nodeId
        return when {
            id.contains(":fuzzy_input:") -> getFuzzyDisplayed(node)
            id.contains(":dynamic_input:") -> getDynamicDisplayed(node)
            id.contains(":random_output:") -> getRandomOutputDisplayed(node)
            else -> ItemRequirementJeiRenderer.getDisplayedStacks(node)
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
    private fun getFuzzyDisplayed(node: PMJeiRequirementNode<ItemRequirementComponent>): List<ItemStack> {
        val comp = node.component
        val groups = comp.properties[RequirementPropertyKeys.FUZZY_INPUTS] as? List<FuzzyInputGroup<ItemStack>>
            ?: return emptyList()
        val parsed = parseAltNodeId(node.nodeId, ":fuzzy_input:")
        val groupIndex = parsed?.groupOrPick ?: node.index
        val group = groups.getOrNull(groupIndex) ?: return emptyList()
        if (group.candidates.isEmpty()) return emptyList()

        val required = group.count.coerceAtLeast(1L)
        val candidateIndex = parsed?.candidate
        val keys = if (candidateIndex != null) {
            listOfNotNull(group.candidates.getOrNull(candidateIndex))
        } else {
            group.candidates
        }

        return keys.mapNotNull { key ->
            val base = key.get()
            if (base.isEmpty) return@mapNotNull null

            val display = base.copy()
            val max = display.maxStackSize.coerceAtLeast(1)
            val desired = required.coerceAtMost(max.toLong()).toInt()
            display.count = desired
            display
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getDynamicDisplayed(node: PMJeiRequirementNode<ItemRequirementComponent>): List<ItemStack> {
        val comp = node.component
        val groups = comp.properties[RequirementPropertyKeys.DYNAMIC_ITEM_INPUTS] as? List<DynamicItemInputGroup>
            ?: return emptyList()

        val parsed = parseAltNodeId(node.nodeId, ":dynamic_input:")
        val groupIndex = parsed?.groupOrPick ?: node.index
        val group = groups.getOrNull(groupIndex) ?: return emptyList()

        val required = group.count.coerceAtLeast(1L)
        val shownKeys = if (group.displayedCandidates.isNotEmpty()) group.displayedCandidates else listOf(group.pattern)
        if (shownKeys.isEmpty()) return emptyList()

        val candidateIndex = parsed?.candidate
        val keys = if (candidateIndex != null) {
            listOfNotNull(shownKeys.getOrNull(candidateIndex))
        } else {
            shownKeys
        }

        return keys.mapNotNull { key ->
            val base = key.get()
            if (base.isEmpty) return@mapNotNull null

            val display = base.copy()
            val max = display.maxStackSize.coerceAtLeast(1)
            val desired = required.coerceAtMost(max.toLong()).toInt()
            display.count = desired
            display
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getRandomOutputDisplayed(node: PMJeiRequirementNode<ItemRequirementComponent>): List<ItemStack> {
        val comp = node.component
        val pool = comp.properties[RequirementPropertyKeys.RANDOM_OUTPUTS] as? RandomOutputPool<ItemStack>
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
            if (base.isEmpty) return@mapNotNull null

            val display = base.copy()
            val max = display.maxStackSize.coerceAtLeast(1)
            val desired = wk.key.count.coerceAtLeast(1L).coerceAtMost(max.toLong()).toInt()
            display.count = desired
            display
        }
    }
}
