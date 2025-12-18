package integration.jei

import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.integration.jei.api.JeiRecipeContext
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutBuilder
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiLayoutRequirementsView
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiPlacedNode
import github.kasuminova.prototypemachinery.integration.jei.api.layout.PMJeiRequirementRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.JeiSlotRole
import github.kasuminova.prototypemachinery.integration.jei.api.render.PMJeiRequirementNode
import github.kasuminova.prototypemachinery.integration.jei.layout.script.AddDecoratorRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.CountAtLeastCondition
import github.kasuminova.prototypemachinery.integration.jei.layout.script.PlaceFixedSlotRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.PlaceGridRule
import github.kasuminova.prototypemachinery.integration.jei.layout.script.ScriptJeiLayoutSpec
import github.kasuminova.prototypemachinery.integration.jei.layout.script.ScriptJeiMachineLayoutDefinition
import net.minecraft.util.ResourceLocation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptJeiMachineLayoutDefinitionTest {

    private class DummySystem<C : RecipeRequirementComponent> : RecipeRequirementSystem<C> {
        override fun start(process: github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess, component: C): github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction {
            throw UnsupportedOperationException("not used")
        }

        override fun onEnd(process: github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess, component: C): github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction {
            throw UnsupportedOperationException("not used")
        }
    }

    private class DummyType(override val id: ResourceLocation) : RecipeRequirementType<DummyComponent> {
        override val system: RecipeRequirementSystem<DummyComponent> = DummySystem()
    }

    private class DummyComponent(override val type: RecipeRequirementType<*>) : RecipeRequirementComponent

    private class RequirementsView(
        override val all: List<PMJeiRequirementNode<out RecipeRequirementComponent>>,
    ) : PMJeiLayoutRequirementsView {
        private val byType = all.groupBy { it.type }
        private val byRole = all.groupBy { it.role }

        override fun byType(type: RecipeRequirementType<*>): List<PMJeiRequirementNode<out RecipeRequirementComponent>> = byType[type].orEmpty()

        override fun byRole(role: PMJeiRequirementRole): List<PMJeiRequirementNode<out RecipeRequirementComponent>> = byRole[role].orEmpty()
    }

    private class CollectingOut : PMJeiLayoutBuilder {
        val placed = mutableListOf<PMJeiPlacedNode>()
        val decorators = mutableListOf<Pair<ResourceLocation, Map<String, Any>>>()
        val fixedSlots = mutableListOf<Triple<ResourceLocation, JeiSlotRole, Pair<Int, Int>>>()

        override fun placeNode(nodeId: String, x: Int, y: Int, variantId: ResourceLocation?) {
            placed += PMJeiPlacedNode(nodeId = nodeId, x = x, y = y, variantId = variantId)
        }

        override fun addDecorator(decoratorId: ResourceLocation, x: Int, y: Int, data: Map<String, Any>) {
            decorators += decoratorId to data
        }

        override fun placeFixedSlot(
            providerId: ResourceLocation,
            role: JeiSlotRole,
            x: Int,
            y: Int,
            width: Int,
            height: Int,
        ) {
            // Only record the essentials for this unit test.
            fixedSlots += Triple(providerId, role, x to y)
        }
    }

    @Test
    fun `placeGrid places nodes in row-major order with variant`() {
        val typeItem = DummyType(ResourceLocation("prototypemachinery", "item"))
        val comp = DummyComponent(typeItem)

        val nodes = listOf(
            PMJeiRequirementNode("c:input:0", typeItem, comp, PMJeiRequirementRole.INPUT, 0),
            PMJeiRequirementNode("c:input:1", typeItem, comp, PMJeiRequirementRole.INPUT, 1),
            PMJeiRequirementNode("c:input:2", typeItem, comp, PMJeiRequirementRole.INPUT, 2),
        )

        val spec = ScriptJeiLayoutSpec(
            width = 200,
            height = 100,
            rules = listOf(
                PlaceGridRule(
                    typeId = typeItem.id,
                    role = PMJeiRequirementRole.INPUT,
                    startX = 10,
                    startY = 20,
                    cols = 2,
                    rows = 2,
                    cellW = 18,
                    cellH = 18,
                    gapX = 2,
                    gapY = 3,
                    variantId = ResourceLocation("prototypemachinery", "slot/18"),
                )
            )
        )

        val layout = ScriptJeiMachineLayoutDefinition(spec)
        val out = CollectingOut()
        layout.build(dummyCtx(), RequirementsView(nodes), out)

        assertEquals(3, out.placed.size)
        assertEquals("c:input:0", out.placed[0].nodeId)
        assertEquals(10, out.placed[0].x)
        assertEquals(20, out.placed[0].y)
        assertEquals(ResourceLocation("prototypemachinery", "slot/18"), out.placed[0].variantId)

        assertEquals("c:input:1", out.placed[1].nodeId)
        assertEquals(10 + 18 + 2, out.placed[1].x)
        assertEquals(20, out.placed[1].y)

        assertEquals("c:input:2", out.placed[2].nodeId)
        assertEquals(10, out.placed[2].x)
        assertEquals(20 + 18 + 3, out.placed[2].y)
    }

    @Test
    fun `condition prevents rule from applying`() {
        val typeFluid = DummyType(ResourceLocation("prototypemachinery", "fluid"))
        val comp = DummyComponent(typeFluid)

        val nodes = listOf(
            PMJeiRequirementNode("c:input:0", typeFluid, comp, PMJeiRequirementRole.INPUT, 0),
        )

        val spec = ScriptJeiLayoutSpec(
            width = 200,
            height = 100,
            rules = listOf(
                PlaceGridRule(
                    condition = CountAtLeastCondition(typeId = typeFluid.id, role = PMJeiRequirementRole.INPUT, min = 2),
                    typeId = typeFluid.id,
                    role = PMJeiRequirementRole.INPUT,
                    startX = 0,
                    startY = 0,
                    cols = 1,
                    rows = 2,
                    cellW = 16,
                    cellH = 58,
                ),
                AddDecoratorRule(
                    condition = CountAtLeastCondition(typeId = typeFluid.id, role = PMJeiRequirementRole.INPUT, min = 2),
                    decoratorId = ResourceLocation("prototypemachinery", "decorator/progress"),
                )
            )
        )

        val out = CollectingOut()
        ScriptJeiMachineLayoutDefinition(spec).build(dummyCtx(), RequirementsView(nodes), out)

        assertTrue(out.placed.isEmpty())
        assertTrue(out.decorators.isEmpty())
    }

    @Test
    fun `mergePerTick makes INPUT match INPUT_PER_TICK in rules and conditions`() {
        val typeFluid = DummyType(ResourceLocation("prototypemachinery", "fluid"))
        val comp = DummyComponent(typeFluid)

        val nodes = listOf(
            PMJeiRequirementNode("c:in:0", typeFluid, comp, PMJeiRequirementRole.INPUT, 0),
            PMJeiRequirementNode("c:inpt:0", typeFluid, comp, PMJeiRequirementRole.INPUT_PER_TICK, 0),
        )

        val spec = ScriptJeiLayoutSpec(
            width = 200,
            height = 100,
            rules = listOf(
                // Should place both nodes because INPUT merges with INPUT_PER_TICK.
                PlaceGridRule(
                    typeId = typeFluid.id,
                    role = PMJeiRequirementRole.INPUT,
                    mergePerTick = true,
                    startX = 5,
                    startY = 6,
                    cols = 2,
                    rows = 1,
                    cellW = 16,
                    cellH = 16,
                    gapX = 1,
                    gapY = 1,
                ),
                // Should also see count==2 under the same merge rule.
                AddDecoratorRule(
                    condition = CountAtLeastCondition(typeId = typeFluid.id, role = PMJeiRequirementRole.INPUT, mergePerTick = true, min = 2),
                    decoratorId = ResourceLocation("prototypemachinery", "decorator/ok"),
                )
            )
        )

        val out = CollectingOut()
        ScriptJeiMachineLayoutDefinition(spec).build(dummyCtx(), RequirementsView(nodes), out)

        assertEquals(listOf("c:in:0", "c:inpt:0"), out.placed.map { it.nodeId })
        assertEquals(1, out.decorators.size)
        assertEquals(ResourceLocation("prototypemachinery", "decorator/ok"), out.decorators[0].first)
    }

    @Test
    fun `placeFixedSlot rule calls layout builder`() {
        val spec = ScriptJeiLayoutSpec(
            width = 200,
            height = 100,
            rules = listOf(
                PlaceFixedSlotRule(
                    providerId = ResourceLocation("prototypemachinery", "fixed/diamond"),
                    role = JeiSlotRole.CATALYST,
                    x = 7,
                    y = 8,
                    width = 18,
                    height = 18,
                )
            )
        )

        val out = CollectingOut()
        ScriptJeiMachineLayoutDefinition(spec).build(dummyCtx(), RequirementsView(emptyList()), out)

        assertEquals(1, out.fixedSlots.size)
        assertEquals(ResourceLocation("prototypemachinery", "fixed/diamond"), out.fixedSlots[0].first)
        assertEquals(JeiSlotRole.CATALYST, out.fixedSlots[0].second)
        assertEquals(7 to 8, out.fixedSlots[0].third)
    }

    private fun dummyCtx(): JeiRecipeContext {
        // Only recipeId is used for logging in ScriptJeiMachineLayoutDefinition.
        val machineType = object : github.kasuminova.prototypemachinery.api.machine.MachineType {
            override val id: ResourceLocation = ResourceLocation("prototypemachinery", "dummy")
            override val name: String = "Dummy"
            override val structure: github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure =
                object : github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure {
                    override val id: String = "dummy"
                    override val orientation: github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation =
                        github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation()
                    override val offset: net.minecraft.util.math.BlockPos = net.minecraft.util.math.BlockPos.ORIGIN
                    override val validators: List<github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator> = emptyList()
                    override val children: List<github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure> = emptyList()

                    override fun createData(): github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData {
                        throw UnsupportedOperationException("not used")
                    }

                    override fun transform(rotation: (net.minecraft.util.EnumFacing) -> net.minecraft.util.EnumFacing): github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure {
                        return this
                    }

                    override fun matches(
                        context: github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext,
                        origin: net.minecraft.util.math.BlockPos,
                    ): Boolean {
                        throw UnsupportedOperationException("not used")
                    }
                }

            override val componentTypes: Set<github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType<*>> = emptySet()
        }

        val recipe = github.kasuminova.prototypemachinery.impl.recipe.SimpleMachineRecipe(
            id = "prototypemachinery:dummy_recipe",
            durationTicks = 20,
            requirements = emptyMap(),
            recipeGroups = emptySet(),
        )

        return JeiRecipeContext(machineType, recipe)
    }
}
