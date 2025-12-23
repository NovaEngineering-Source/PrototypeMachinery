package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentNode
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.StructureComponentMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureEnergyContainer
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnergyRequirementSystemPerTickTest {

    @Test
    fun `per tick drains energy from output containers`() {
        val machine = DummyMachineInstance()
        val src = DummyEnergyContainer(machine, capacity = 100, initial = 100, allowed = setOf(PortMode.OUTPUT))
        machine.structureComponentMap.add(src)

        val process = dummyProcess(machine)
        val component = EnergyRequirementComponent(
            id = "e",
            inputPerTick = 30L,
        )

        val tx = EnergyRequirementSystem.acquireTickTransaction(process, component)
        assertEquals(ProcessResult.Success, tx.result)
        tx.commit()

        assertEquals(70L, src.stored)
    }

    @Test
    fun `per tick drain blocked when insufficient energy`() {
        val machine = DummyMachineInstance()
        val src = DummyEnergyContainer(machine, capacity = 100, initial = 10, allowed = setOf(PortMode.OUTPUT))
        machine.structureComponentMap.add(src)

        val process = dummyProcess(machine)
        val component = EnergyRequirementComponent(
            id = "e",
            inputPerTick = 30L,
        )

        val tx = EnergyRequirementSystem.acquireTickTransaction(process, component)
        assertTrue(tx.result is ProcessResult.Blocked)
        tx.commit()

        // Must have no side effects
        assertEquals(10L, src.stored)
    }

    @Test
    fun `per tick outputs energy into input containers`() {
        val machine = DummyMachineInstance()
        val dst = DummyEnergyContainer(machine, capacity = 100, initial = 0, allowed = setOf(PortMode.INPUT))
        machine.structureComponentMap.add(dst)

        val process = dummyProcess(machine)
        val component = EnergyRequirementComponent(
            id = "e",
            outputPerTick = 50L,
        )

        val tx = EnergyRequirementSystem.acquireTickTransaction(process, component)
        assertEquals(ProcessResult.Success, tx.result)
        tx.commit()

        assertEquals(50L, dst.stored)
    }

    @Test
    fun `per tick output blocked when full unless ignore_output_full`() {
        val machine = DummyMachineInstance()
        val dst = DummyEnergyContainer(machine, capacity = 10, initial = 10, allowed = setOf(PortMode.INPUT))
        machine.structureComponentMap.add(dst)

        val process = dummyProcess(machine)
        val component = EnergyRequirementComponent(
            id = "e",
            outputPerTick = 5L,
        )

        val tx = EnergyRequirementSystem.acquireTickTransaction(process, component)
        assertTrue(tx.result is ProcessResult.Blocked)
        tx.commit()

        assertEquals(10L, dst.stored)
    }

    @Test
    fun `per tick output allows partial when ignore_output_full`() {
        val machine = DummyMachineInstance()
        val dst = DummyEnergyContainer(machine, capacity = 10, initial = 10, allowed = setOf(PortMode.INPUT))
        machine.structureComponentMap.add(dst)

        val process = dummyProcess(machine)
        val component = EnergyRequirementComponent(
            id = "e",
            outputPerTick = 5L,
            properties = mapOf(
                "ignore_output_full" to true,
            ),
        )

        val tx = EnergyRequirementSystem.acquireTickTransaction(process, component)
        assertEquals(ProcessResult.Success, tx.result)
        tx.commit()

        assertEquals(10L, dst.stored)
    }

    @Test
    fun `per tick rollback restores extracted energy on failure`() {
        val machine = DummyMachineInstance()
        val src = DummyEnergyContainer(
            owner = machine,
            capacity = 100,
            initial = 50,
            allowed = setOf(PortMode.OUTPUT),
            simulateExtractAlways = 50, // simulate claims it can provide
            executeExtractAlways = 0,   // execute provides nothing -> inconsistent
        )
        machine.structureComponentMap.add(src)

        val process = dummyProcess(machine)
        val component = EnergyRequirementComponent(
            id = "e",
            inputPerTick = 30L,
        )

        val tx = EnergyRequirementSystem.acquireTickTransaction(process, component)
        assertTrue(tx.result is ProcessResult.Failure)

        // If any partial changes were applied, rollback must restore.
        tx.rollback()
        assertEquals(50L, src.stored)
    }

    private fun dummyProcess(machine: MachineInstance): RecipeProcess {
        val recipe = object : MachineRecipe {
            override val id: String = "dummy"
            override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>> = emptyMap()
        }
        return RecipeProcessImpl(machine, recipe, seed = 42L)
    }

    private class DummyEnergyContainer(
        override val owner: MachineInstance,
        override val provider: Any? = null,
        override val capacity: Long,
        initial: Long,
        private val allowed: Set<PortMode>,
        private val simulateExtractAlways: Long? = null,
        private val executeExtractAlways: Long? = null,
    ) : StructureEnergyContainer {

        private var _stored: Long = initial

        override val stored: Long
            get() = _stored

        override fun isAllowedPortMode(ioType: PortMode): Boolean = allowed.contains(ioType)

        override fun insertEnergy(amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L
            if (!isAllowedPortMode(PortMode.INPUT)) return 0L
            return insertEnergyUnchecked(amount, action)
        }

        override fun extractEnergy(amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L
            if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L
            return extractEnergyUnchecked(amount, action)
        }

        override fun insertEnergyUnchecked(amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L
            val accepted = minOf(amount, capacity - _stored).coerceAtLeast(0L)
            if (action == TransactionMode.EXECUTE) {
                _stored += accepted
            }
            return accepted
        }

        override fun extractEnergyUnchecked(amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L

            if (action == TransactionMode.SIMULATE && simulateExtractAlways != null) {
                return minOf(amount, simulateExtractAlways)
            }
            if (action == TransactionMode.EXECUTE && executeExtractAlways != null) {
                val out = minOf(amount, executeExtractAlways)
                _stored = (_stored - out).coerceAtLeast(0L)
                return out
            }

            val extracted = minOf(amount, _stored).coerceAtLeast(0L)
            if (action == TransactionMode.EXECUTE) {
                _stored -= extracted
            }
            return extracted
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

        override fun remove(key: MachineComponentType<*>) {}

        override fun contains(key: MachineComponentType<*>): Boolean = false

        override fun clear() {}

        override fun add(component: MachineComponent) {}

        override fun remove(component: MachineComponent) {}

        override fun <C : MachineComponent> getByInstanceOf(clazz: Class<out C>): Collection<C> = emptyList()
    }
}
