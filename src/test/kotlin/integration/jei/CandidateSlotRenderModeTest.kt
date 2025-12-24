package integration.jei

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.FuzzyInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RandomOutputPool
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.WeightedKey
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient.VanillaItemNodeIngredientProvider
import github.kasuminova.prototypemachinery.integration.jei.builtin.requirement.ItemRequirementJeiRenderer
import github.kasuminova.prototypemachinery.integration.jei.runtime.JeiRenderOptions
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CandidateSlotRenderModeTest {

    private fun dummyCtx(): JeiRecipeContext {
        // split/provider code paths used in this test do not touch ctx fields.
        val mt = object : MachineType {
            override val id: ResourceLocation = ResourceLocation("test", "machine")
            override val name: String = "Test Machine"
            override val structure: MachineStructure
                get() = throw UnsupportedOperationException("Not needed by this test")
            override val componentTypes = emptySet<github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType<*>>()
        }

        val recipe = object : MachineRecipe {
            override val id: String = "test:recipe"
            override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>> = emptyMap()
        }

        return JeiRecipeContext(machineType = mt, recipe = recipe)
    }

    @Test
    fun `expanded mode splits fuzzy and random into candidate slots`() {
        val prev = JeiRenderOptions.candidateSlotRenderMode
        JeiRenderOptions.candidateSlotRenderMode = JeiRenderOptions.CandidateSlotRenderMode.EXPANDED
        try {
            val a = PMItemKeyType.create(ItemStack(Items.IRON_INGOT, 1))
            val b = PMItemKeyType.create(ItemStack(Items.GOLD_INGOT, 1))
            val c = PMItemKeyType.create(ItemStack(Items.DIAMOND, 1))

            val fuzzy = listOf(
                FuzzyInputGroup(candidates = listOf(a, b), count = 3L)
            )

            val random = RandomOutputPool(
                candidates = listOf(
                    WeightedKey(key = b.copy().also { it.count = 2L }, weight = 1),
                    WeightedKey(key = c.copy().also { it.count = 1L }, weight = 5),
                ),
                pickCount = 1,
            )

            val comp = ItemRequirementComponent(
                id = "c1",
                inputs = emptyList(),
                outputs = emptyList(),
                properties = mapOf(
                    RequirementPropertyKeys.FUZZY_INPUTS to fuzzy,
                    RequirementPropertyKeys.RANDOM_OUTPUTS to random,
                )
            )

            val nodes = ItemRequirementJeiRenderer.split(dummyCtx(), comp)

            // fuzzy: 2 candidates => 2 nodes
            // random: 2 candidates => 2 nodes
            assertEquals(4, nodes.size)
            assertEquals(listOf("c1:fuzzy_input:0:0", "c1:fuzzy_input:0:1", "c1:random_output:0:0", "c1:random_output:0:1"), nodes.map { it.nodeId })
        } finally {
            JeiRenderOptions.candidateSlotRenderMode = prev
        }
    }

    @Test
    fun `provider returns singleton list for expanded candidate node`() {
        val a = PMItemKeyType.create(ItemStack(Items.IRON_INGOT, 1))
        val b = PMItemKeyType.create(ItemStack(Items.GOLD_INGOT, 1))

        val fuzzy = listOf(
            FuzzyInputGroup(candidates = listOf(a, b), count = 5L)
        )

        val comp = ItemRequirementComponent(
            id = "c1",
            properties = mapOf(
                RequirementPropertyKeys.FUZZY_INPUTS to fuzzy,
            )
        )

        val node = PMJeiRequirementNode(
            nodeId = "c1:fuzzy_input:0:1",
            type = comp.type as github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType<ItemRequirementComponent>,
            component = comp,
            role = PMJeiRequirementRole.INPUT,
            index = 0,
        )

        val displayed = VanillaItemNodeIngredientProvider.getDisplayed(dummyCtx(), node)
        assertEquals(1, displayed.size)
        assertEquals(Items.GOLD_INGOT, displayed[0].item)
        // fuzzy count is clamped to max stack size; GOLD_INGOT max is 64 so should become 5
        assertEquals(5, displayed[0].count)
    }
}
