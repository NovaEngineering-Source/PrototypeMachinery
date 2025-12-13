package github.kasuminova.prototypemachinery.impl.recipe.requirement.component.system

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentNode
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirement
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.api.recipe.selective.SelectiveModifierRegistry
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeModifierImpl
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.component.SelectiveRequirementComponent
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class SelectiveRequirementSystemTest {

    @BeforeEach
    fun resetRegistry() {
        SelectiveModifierRegistry.clearForTests()
    }

    @Test
    fun `selects first success candidate and defers commit`() {
        val blocked = CountersSystem(ProcessResult.Blocked("blocked"))
        val failure = CountersSystem(ProcessResult.Failure("fail", listOf()))
        val success = CountersSystem(ProcessResult.Success)

        val candidates = listOf(
            DummyReqComponent("blocked", DummyReqType("blocked", blocked)),
            DummyReqComponent("failure", DummyReqType("failure", failure)),
            DummyReqComponent("success", DummyReqType("success", success)),
        )

        val machine = DummyMachineInstance()
        val process = dummyProcess(machine)

        val wrapper = SelectiveRequirementComponent(
            id = "sel",
            candidates = candidates,
            modifierIds = emptyList(),
        )

        val tx = SelectiveRequirementSystem.start(machine, wrapper, process)

        // selection probes: blocked -> commit, failure -> rollback, success chosen but not committed yet
        assertEquals(1, blocked.startCommitCount)
        assertEquals(1, failure.startRollbackCount)
        assertEquals(0, success.startCommitCount)

        tx.commit()
        assertEquals(1, success.startCommitCount)
    }

    @Test
    fun `locks selection for future ticks`() {
        val first = TickCountersSystem(ProcessResult.Success)
        val second = TickCountersSystem(ProcessResult.Success)

        val candidates = listOf(
            DummyReqComponent("first", DummyReqType("first", first)),
            DummyReqComponent("second", DummyReqType("second", second)),
        )

        val machine = DummyMachineInstance()
        val process = dummyProcess(machine)

        val wrapper = SelectiveRequirementComponent(
            id = "sel",
            candidates = candidates,
            modifierIds = emptyList(),
        )

        SelectiveRequirementSystem.start(machine, wrapper, process).commit()

        // Tick should use the first candidate only
        SelectiveRequirementSystem.acquireTickTransaction(machine, wrapper, process).commit()
        SelectiveRequirementSystem.acquireTickTransaction(machine, wrapper, process).commit()

        assertEquals(2, first.tickCommitCount)
        assertEquals(0, second.tickCommitCount)
    }

    @Test
    fun `commit invokes modifier and onEnd cleans it up`() {
        val success = CountersSystem(ProcessResult.Success)
        val candidates = listOf(
            DummyReqComponent("success", DummyReqType("success", success)),
        )

        SelectiveModifierRegistry.register("speedup") { ctx ->
            ctx.putProcessAttributeModifier(
                github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes.PROCESS_SPEED,
                MachineAttributeModifierImpl.multiplyTotal("speed", 0.25)
            )
        }

        val machine = DummyMachineInstance()
        val process = dummyProcess(machine)

        val wrapper = SelectiveRequirementComponent(
            id = "sel",
            candidates = candidates,
            modifierIds = listOf("speedup"),
        )

        SelectiveRequirementSystem.start(machine, wrapper, process).commit()

        val speedEntry = process.attributeMap.attributes.entries.firstOrNull {
            it.key.id == github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes.PROCESS_SPEED.id
        }
        assertNotNull(speedEntry)
        assertTrue(speedEntry!!.value.modifiers.isNotEmpty(), "expected speed modifiers to be applied")

        SelectiveRequirementSystem.onEnd(machine, wrapper, process).commit()

        val speedEntryAfter = process.attributeMap.attributes.entries.firstOrNull {
            it.key.id == github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes.PROCESS_SPEED.id
        }
        assertNotNull(speedEntryAfter)
        assertTrue(speedEntryAfter!!.value.modifiers.isEmpty(), "expected speed modifiers to be cleaned up")
    }

    private fun dummyProcess(machine: MachineInstance): RecipeProcess {
        val recipe = object : MachineRecipe {
            override val id: String = "dummy"
            override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirement>> = emptyMap()
        }
        return RecipeProcessImpl(machine, recipe, seed = 42L)
    }

    private data class DummyReqComponent(
        val name: String,
        override val type: RecipeRequirementType<*>,
    ) : RecipeRequirementComponent

    private class DummyReqType(
        path: String,
        override val system: RecipeRequirementSystem<DummyReqComponent>,
    ) : RecipeRequirementType<DummyReqComponent> {
        override val id: ResourceLocation = ResourceLocation("test", path)
    }

    private open class CountersSystem(
        private val startResult: ProcessResult,
    ) : RecipeRequirementSystem<DummyReqComponent> {

        var startCommitCount: Int = 0
        var startRollbackCount: Int = 0

        override fun start(machine: MachineInstance, component: DummyReqComponent, process: RecipeProcess): RequirementTransaction {
            return object : RequirementTransaction {
                override val result: ProcessResult = startResult
                override fun commit() {
                    startCommitCount++
                }

                override fun rollback() {
                    startRollbackCount++
                }
            }
        }

        override fun onEnd(machine: MachineInstance, component: DummyReqComponent, process: RecipeProcess): RequirementTransaction {
            return object : RequirementTransaction {
                override val result: ProcessResult = ProcessResult.Success
                override fun commit() {}
                override fun rollback() {}
            }
        }
    }

    private class TickCountersSystem(
        startResult: ProcessResult,
    ) : CountersSystem(startResult), RecipeRequirementSystem.Tickable<DummyReqComponent> {

        var tickCommitCount: Int = 0

        override fun acquireTickTransaction(machine: MachineInstance, component: DummyReqComponent, process: RecipeProcess): RequirementTransaction {
            return object : RequirementTransaction {
                override val result: ProcessResult = ProcessResult.Success
                override fun commit() {
                    tickCommitCount++
                }

                override fun rollback() {}
            }
        }
    }

    private class DummyMachineInstance : MachineInstance {

        override val type: MachineType = object : MachineType {
            override val id: ResourceLocation = ResourceLocation("test", "dummy")
            override val name: String = "dummy"
            override val structure: MachineStructure = object : MachineStructure {
                override val id: String = "dummy"
                override val orientation: StructureOrientation = StructureOrientation(front = EnumFacing.NORTH, top = EnumFacing.UP)
                override val offset: BlockPos = BlockPos.ORIGIN
                override val validators: List<StructureValidator> = emptyList()
                override val children: List<MachineStructure> = emptyList()
                override fun createData() = throw UnsupportedOperationException()
                override fun transform(rotation: (EnumFacing) -> EnumFacing): MachineStructure = this
                override fun matches(context: StructureMatchContext, origin: BlockPos): Boolean = true
            }
            override val componentTypes: Set<MachineComponentType<*>> = emptySet()
        }

        override val blockEntity: BlockEntity = object : TileEntity() {}

        override val componentMap: MachineComponentMap = DummyMachineComponentMap()

        override val attributeMap: MachineAttributeMap = MachineAttributeMapImpl()

        override fun isFormed(): Boolean = true

        override fun syncComponent(component: MachineComponent.Synchronizable) {}
    }

    private class DummyMachineComponentMap : MachineComponentMap {

        override val components: Map<MachineComponentType<*>, MachineComponent> = emptyMap()

        override val systems: List<MachineSystem<*>> = emptyList()

        override val orderedComponents: List<TopologicalComponentNode<MachineComponentType<*>, MachineComponent>> = emptyList()

        override fun get(key: MachineComponentType<*>): MachineComponent? = null

        override fun addDependency(dependentKey: MachineComponentType<*>, dependencyKey: MachineComponentType<*>) {}

        override fun removeDependency(dependentKey: MachineComponentType<*>, dependencyKey: MachineComponentType<*>) {}

        override fun add(key: MachineComponentType<*>, component: MachineComponent, dependencies: Set<MachineComponentType<*>>) {}

        override fun addAfter(targetKey: MachineComponentType<*>, key: MachineComponentType<*>, component: MachineComponent) {}

        override fun addBefore(targetKey: MachineComponentType<*>, key: MachineComponentType<*>, component: MachineComponent) {}

        override fun addFirst(key: MachineComponentType<*>, component: MachineComponent) {}

        override fun addTail(key: MachineComponentType<*>, component: MachineComponent) {}

        override fun remove(key: MachineComponentType<*>) {}

        override fun contains(key: MachineComponentType<*>): Boolean = false

        override fun clear() {}

        override fun add(component: MachineComponent) {}

        override fun remove(component: MachineComponent) {}

        override fun <C : MachineComponent> getByInstanceOf(clazz: Class<out C>): Collection<C> = emptyList()
    }
}
