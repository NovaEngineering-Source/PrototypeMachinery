package github.kasuminova.prototypemachinery.impl.recipe

import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RecipeGroupTest {

    @Test
    fun `recipe manager can lookup recipes by groups`() {
        val snapshot = RecipeManagerImpl.snapshotForTests()
        try {
            RecipeManagerImpl.clearForTests()

            val gA = ResourceLocation("test", "group_a")
            val gB = ResourceLocation("test", "group_b")

            val r1 = DummyRecipe("r1", setOf(gA))
            val r2 = DummyRecipe("r2", setOf(gB))
            val r3 = DummyRecipe("r3", setOf(gA, gB))
            val r4 = DummyRecipe("r4", emptySet())

            RecipeManagerImpl.register(r1)
            RecipeManagerImpl.register(r2)
            RecipeManagerImpl.register(r3)
            RecipeManagerImpl.register(r4)

            assertEquals(setOf("r1", "r3"), RecipeManagerImpl.getByGroup(gA).map { it.id }.toSet())
            assertEquals(setOf("r2", "r3"), RecipeManagerImpl.getByGroup(gB).map { it.id }.toSet())

            val union = RecipeManagerImpl.getByGroups(setOf(gA, gB)).map { it.id }.toSet()
            assertEquals(setOf("r1", "r2", "r3"), union)

            assertTrue(RecipeManagerImpl.getByGroups(emptySet()).isEmpty())
        } finally {
            RecipeManagerImpl.restoreForTests(snapshot)
        }
    }

    private class DummyRecipe(
        override val id: String,
        override val recipeGroups: Set<ResourceLocation>,
        override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>> = emptyMap(),
    ) : MachineRecipe
}
