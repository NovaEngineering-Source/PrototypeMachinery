package github.kasuminova.prototypemachinery.impl.machine.component.system

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentNode
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeExecutor
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RecipeRequirementSystem
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.system.RequirementTransaction
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.StructureComponentMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FactoryRecipeProcessorSystemTest {

    @Test
    fun `start blocked is atomic and does not start`() {
        val sysA = SideEffectSystem(
            startResult = ProcessResult.Success,
            tickResult = ProcessResult.Success,
            endResult = ProcessResult.Success,
        )
        val sysB = SideEffectSystem(
            startResult = ProcessResult.Blocked("blocked.start"),
            tickResult = ProcessResult.Success,
            endResult = ProcessResult.Success,
        )

        val typeA = DummyReqType("a", sysA)
        val typeB = DummyReqType("b", sysB)

        val reqA = DummyReqComponent("A", typeA)
        val reqB = DummyReqComponent("B", typeB)

        val machine = DummyMachineInstance()
        val process = RecipeProcessImpl(machine, DummyRecipe(durationTicks = 10, requirements = mapOf(typeA to listOf(reqA), typeB to listOf(reqB))), seed = 42L)

        val processor = DummyProcessorComponent(machine, mutableListOf(process))

        FactoryRecipeProcessorSystem.onTick(machine, processor)

        assertEquals(1, sysA.startAcquireCount)
        assertEquals(0, sysA.startCommitCount)
        assertEquals(1, sysA.startRollbackCount, "successful start acquisitions must rollback when overall stage is blocked")

        assertEquals("blocked.start", process.status.message)
        assertFalse(process.status.isError)
        assertEquals(0.0f, process.status.progress)
        assertEquals(1, processor.activeProcesses.size)
    }

    @Test
    fun `tick blocked is atomic and does not advance progress`() {
        val sysA = SideEffectSystem(
            startResult = ProcessResult.Success,
            tickResult = ProcessResult.Success,
            endResult = ProcessResult.Success,
        )
        val sysB = SideEffectSystem(
            startResult = ProcessResult.Success,
            tickResult = ProcessResult.Blocked("blocked.tick"),
            endResult = ProcessResult.Success,
        )

        val typeA = DummyReqType("a", sysA)
        val typeB = DummyReqType("b", sysB)

        val reqA = DummyReqComponent("A", typeA)
        val reqB = DummyReqComponent("B", typeB)

        val machine = DummyMachineInstance()
        val process = RecipeProcessImpl(machine, DummyRecipe(durationTicks = 10, requirements = mapOf(typeA to listOf(reqA), typeB to listOf(reqB))), seed = 42L)

        val processor = DummyProcessorComponent(machine, mutableListOf(process))

        // First tick: start succeeds, then tick stage is attempted and gets blocked.
        FactoryRecipeProcessorSystem.onTick(machine, processor)

        assertEquals(1, sysA.startAcquireCount)
        assertEquals(1, sysA.startCommitCount)
        assertEquals(1, sysB.startAcquireCount)
        assertEquals(1, sysB.startCommitCount)

        assertEquals(1, sysA.tickAcquireCount)
        assertEquals(0, sysA.tickCommitCount)
        assertEquals(1, sysA.tickRollbackCount)

        assertEquals("blocked.tick", process.status.message)
        assertEquals(0.0f, process.status.progress)
        assertEquals(1, processor.activeProcesses.size)

        // Second tick: start should NOT run again; tick blocked again, still no progress.
        FactoryRecipeProcessorSystem.onTick(machine, processor)

        assertEquals(1, sysA.startAcquireCount)
        assertEquals(2, sysA.tickAcquireCount)
        assertEquals(2, sysA.tickRollbackCount)
        assertEquals(0.0f, process.status.progress)
    }

    @Test
    fun `finish blocked rolls back end stage and keeps process`() {
        val sysA = SideEffectSystem(
            startResult = ProcessResult.Success,
            tickResult = ProcessResult.Success,
            endResult = ProcessResult.Success,
        )
        val sysB = SideEffectSystem(
            startResult = ProcessResult.Success,
            tickResult = ProcessResult.Success,
            endResult = ProcessResult.Blocked("blocked.end"),
        )

        val typeA = DummyReqType("a", sysA)
        val typeB = DummyReqType("b", sysB)

        val reqA = DummyReqComponent("A", typeA)
        val reqB = DummyReqComponent("B", typeB)

        val machine = DummyMachineInstance()
        val process = RecipeProcessImpl(machine, DummyRecipe(durationTicks = 1, requirements = mapOf(typeA to listOf(reqA), typeB to listOf(reqB))), seed = 42L)

        val processor = DummyProcessorComponent(machine, mutableListOf(process))

        // Tick once: start + tick will advance progress to 1 and try end.
        FactoryRecipeProcessorSystem.onTick(machine, processor)

        assertEquals(1.0f, process.status.progress)
        assertEquals("blocked.end", process.status.message)
        assertTrue(processor.activeProcesses.contains(process), "process should remain active if end stage is blocked")

        assertEquals(1, sysA.endAcquireCount)
        assertEquals(0, sysA.endCommitCount)
        assertEquals(1, sysA.endRollbackCount)
    }

    @Test
    fun `success path completes and removes process`() {
        val sysA = SideEffectSystem(
            startResult = ProcessResult.Success,
            tickResult = ProcessResult.Success,
            endResult = ProcessResult.Success,
        )

        val typeA = DummyReqType("a", sysA)
        val reqA = DummyReqComponent("A", typeA)

        val machine = DummyMachineInstance()
        val process = RecipeProcessImpl(machine, DummyRecipe(durationTicks = 1, requirements = mapOf(typeA to listOf(reqA))), seed = 42L)

        val processor = DummyProcessorComponent(machine, mutableListOf(process))

        FactoryRecipeProcessorSystem.onTick(machine, processor)

        assertTrue(processor.activeProcesses.isEmpty())
        assertEquals(1, sysA.startCommitCount)
        assertEquals(1, sysA.tickCommitCount)
        assertEquals(1, sysA.endCommitCount)
        assertEquals(0, sysA.startRollbackCount)
        assertEquals(0, sysA.tickRollbackCount)
        assertEquals(0, sysA.endRollbackCount)
    }

    private class DummyRecipe(
        override val durationTicks: Int,
        override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>>,
    ) : MachineRecipe {
        override val id: String = "dummy"
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

    private class SideEffectSystem(
        private val startResult: ProcessResult,
        private val tickResult: ProcessResult,
        private val endResult: ProcessResult,
    ) : RecipeRequirementSystem.Tickable<DummyReqComponent> {

        var startAcquireCount: Int = 0
        var startCommitCount: Int = 0
        var startRollbackCount: Int = 0

        var tickAcquireCount: Int = 0
        var tickCommitCount: Int = 0
        var tickRollbackCount: Int = 0

        var endAcquireCount: Int = 0
        var endCommitCount: Int = 0
        var endRollbackCount: Int = 0

        override fun start(process: RecipeProcess, component: DummyReqComponent): RequirementTransaction {
            if (startResult is ProcessResult.Blocked) {
                return tx(startResult)
            }

            // Side effect is applied on acquisition.
            startAcquireCount++
            return tx(startResult,
                commit = { startCommitCount++ },
                rollback = { startRollbackCount++ }
            )
        }

        override fun acquireTickTransaction(process: RecipeProcess, component: DummyReqComponent): RequirementTransaction {
            if (tickResult is ProcessResult.Blocked) {
                return tx(tickResult)
            }

            tickAcquireCount++
            return tx(tickResult,
                commit = { tickCommitCount++ },
                rollback = { tickRollbackCount++ }
            )
        }

        override fun onEnd(process: RecipeProcess, component: DummyReqComponent): RequirementTransaction {
            if (endResult is ProcessResult.Blocked) {
                return tx(endResult)
            }

            endAcquireCount++
            return tx(endResult,
                commit = { endCommitCount++ },
                rollback = { endRollbackCount++ }
            )
        }

        private fun tx(result: ProcessResult, commit: () -> Unit = {}, rollback: () -> Unit = {}): RequirementTransaction {
            return object : RequirementTransaction {
                override val result: ProcessResult = result
                override fun commit() = commit()
                override fun rollback() = rollback()
            }
        }
    }

    private class DummyProcessorComponent(
        override val owner: MachineInstance,
        override val activeProcesses: MutableCollection<RecipeProcess>,
        override val provider: Any? = null,
    ) : FactoryRecipeProcessorComponent {

        override val type: MachineComponentType<*> = object : MachineComponentType<FactoryRecipeProcessorComponent> {
            override val id: ResourceLocation = ResourceLocation("test", "processor")
            override val system: MachineSystem<FactoryRecipeProcessorComponent>? = null
            override fun createComponent(machine: MachineInstance): FactoryRecipeProcessorComponent {
                error("Not used in tests")
            }
        }

        override val maxConcurrentProcesses: Int = 1

        override val executors: MutableList<RecipeExecutor> = ArrayList()

        override fun startProcess(process: RecipeProcess): Boolean {
            return activeProcesses.add(process)
        }

        override fun stopProcess(process: RecipeProcess) {
            activeProcesses.remove(process)
        }

        override fun tickProcesses() {
            // no-op
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

        override val structureComponentMap: StructureComponentMap = StructureComponentMapImpl()

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

        override fun remove(key: MachineComponentType<*>): Unit = Unit

        override fun contains(key: MachineComponentType<*>): Boolean = false

        override fun clear(): Unit = Unit

        override fun add(component: MachineComponent) {}

        override fun remove(component: MachineComponent) {}

        override fun <C : MachineComponent> getByInstanceOf(clazz: Class<out C>): Collection<C> = emptyList()
    }
}
