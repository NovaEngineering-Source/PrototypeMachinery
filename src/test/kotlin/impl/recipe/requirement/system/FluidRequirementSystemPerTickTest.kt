package github.kasuminova.prototypemachinery.impl.recipe.requirement.system

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentNode
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponentMap
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureFluidKeyContainer
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
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.StructureComponentMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import net.minecraft.init.Bootstrap
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class FluidRequirementSystemPerTickTest {

    companion object {
        private lateinit var fluidA: Fluid

        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            // Forge's FluidStack triggers FluidRegistry static init, which references Blocks.
            // Unit tests run without a full game bootstrap, so we need to init it manually.
            if (!Bootstrap.isRegistered()) {
                Bootstrap.register()
            }

            fluidA = Fluid("test_fluid_a", ResourceLocation("test", "still"), ResourceLocation("test", "flow"))
            if (!FluidRegistry.isFluidRegistered(fluidA)) {
                FluidRegistry.registerFluid(fluidA)
            }
        }
    }

    @Test
    fun `per tick drains fluid from output containers`() {
        val machine = DummyMachineInstance()
        val src = DummyFluidContainer(machine, capacity = 1000, initialFluid = fluidA, initialAmount = 500, allowed = setOf(PortMode.OUTPUT))
        machine.structureComponentMap.add(src)

        val process = dummyProcess(machine)
        val component = FluidRequirementComponent(
            id = "f",
            inputsPerTick = listOf(fluidKey(100)),
        )

        val tx = FluidRequirementSystem.acquireTickTransaction(process, component)
        assertEquals(ProcessResult.Success, tx.result)
        tx.commit()

        assertEquals(400L, src.getFluidAmount(0))
    }

    @Test
    fun `per tick drain blocked when insufficient fluid`() {
        val machine = DummyMachineInstance()
        val src = DummyFluidContainer(machine, capacity = 1000, initialFluid = fluidA, initialAmount = 50, allowed = setOf(PortMode.OUTPUT))
        machine.structureComponentMap.add(src)

        val process = dummyProcess(machine)
        val component = FluidRequirementComponent(
            id = "f",
            inputsPerTick = listOf(fluidKey(100)),
        )

        val tx = FluidRequirementSystem.acquireTickTransaction(process, component)
        assertTrue(tx.result is ProcessResult.Blocked)
        tx.commit()

        // Must have no side effects
        assertEquals(50L, src.getFluidAmount(0))
    }

    @Test
    fun `per tick outputs fluid into input containers`() {
        val machine = DummyMachineInstance()
        val dst = DummyFluidContainer(machine, capacity = 1000, initialFluid = fluidA, initialAmount = 0, allowed = setOf(PortMode.INPUT))
        machine.structureComponentMap.add(dst)

        val process = dummyProcess(machine)
        val component = FluidRequirementComponent(
            id = "f",
            outputsPerTick = listOf(fluidKey(250)),
        )

        val tx = FluidRequirementSystem.acquireTickTransaction(process, component)
        assertEquals(ProcessResult.Success, tx.result)
        tx.commit()

        assertEquals(250L, dst.getFluidAmount(0))
    }

    @Test
    fun `per tick output blocked when full unless ignore_output_full`() {
        val machine = DummyMachineInstance()
        val dst = DummyFluidContainer(machine, capacity = 100, initialFluid = fluidA, initialAmount = 100, allowed = setOf(PortMode.INPUT))
        machine.structureComponentMap.add(dst)

        val process = dummyProcess(machine)
        val component = FluidRequirementComponent(
            id = "f",
            outputsPerTick = listOf(fluidKey(10)),
        )

        val tx = FluidRequirementSystem.acquireTickTransaction(process, component)
        assertTrue(tx.result is ProcessResult.Blocked)
        tx.commit()

        assertEquals(100L, dst.getFluidAmount(0))
    }

    @Test
    fun `per tick output allows partial when ignore_output_full`() {
        val machine = DummyMachineInstance()
        val dst = DummyFluidContainer(machine, capacity = 100, initialFluid = fluidA, initialAmount = 95, allowed = setOf(PortMode.INPUT))
        machine.structureComponentMap.add(dst)

        val process = dummyProcess(machine)
        val component = FluidRequirementComponent(
            id = "f",
            outputsPerTick = listOf(fluidKey(10)),
            properties = mapOf("ignore_output_full" to true),
        )

        val tx = FluidRequirementSystem.acquireTickTransaction(process, component)
        assertEquals(ProcessResult.Success, tx.result)
        tx.commit()

        assertEquals(100L, dst.getFluidAmount(0))
    }

    @Test
    fun `per tick rollback restores extracted fluid on failure`() {
        val machine = DummyMachineInstance()
        val src = DummyFluidContainer(
            owner = machine,
            capacity = 1000,
            initialFluid = fluidA,
            initialAmount = 200,
            allowed = setOf(PortMode.OUTPUT),
            simulateExtractAlways = 200,
            executeExtractAlways = 0,
        )
        machine.structureComponentMap.add(src)

        val process = dummyProcess(machine)
        val component = FluidRequirementComponent(
            id = "f",
            inputsPerTick = listOf(fluidKey(100)),
        )

        val tx = FluidRequirementSystem.acquireTickTransaction(process, component)
        assertTrue(tx.result is ProcessResult.Failure)
        tx.rollback()

        assertEquals(200L, src.getFluidAmount(0))
    }

    private fun dummyProcess(machine: MachineInstance): RecipeProcess {
        val recipe = object : MachineRecipe {
            override val id: String = "dummy"
            override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>> = emptyMap()
        }
        return RecipeProcessImpl(machine, recipe, seed = 42L)
    }

    private fun fluidKey(amount: Long): PMKey<FluidStack> {
        // PMFluidKeyType.create uses FluidStack.amount to seed count.
        // Tests here stay in Int range.
        return PMFluidKeyType.create(FluidStack(fluidA, amount.toInt())).apply { count = amount }
    }

    private class DummyFluidContainer(
        override val owner: MachineInstance,
        override val provider: Any? = null,
        private val capacity: Long,
        initialFluid: Fluid,
        initialAmount: Long,
        private val allowed: Set<PortMode>,
        private val simulateExtractAlways: Long? = null,
        private val executeExtractAlways: Long? = null,
    ) : StructureFluidKeyContainer {

        private val fluid: Fluid = initialFluid
        private var amount: Long = initialAmount.coerceIn(0L, capacity)

        override fun isAllowedPortMode(ioType: PortMode): Boolean = allowed.contains(ioType)

        fun getFluidAmount(tank: Int): Long = if (tank == 0) amount else 0L

        fun setFluidAmount(tank: Int, amount: Long) {
            if (tank != 0) return
            this.amount = amount.coerceIn(0L, capacity)
        }

        override fun insert(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L
            if (!isAllowedPortMode(PortMode.INPUT)) return 0L
            return insertUnchecked(key, amount, action)
        }

        override fun insertUnchecked(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L
            val stack = key.get()
            if (stack.fluid != this.fluid) return 0L

            val space = (capacity - this.amount).coerceAtLeast(0L)
            val accepted = minOf(amount, space)
            if (accepted <= 0L) return 0L

            if (action == TransactionMode.EXECUTE) {
                this.amount += accepted
            }
            return accepted
        }

        override fun extract(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L
            if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L
            return extractUnchecked(key, amount, action)
        }

        override fun extractUnchecked(key: PMKey<FluidStack>, amount: Long, action: TransactionMode): Long {
            if (amount <= 0L) return 0L
            val stack = key.get()
            if (stack.fluid != this.fluid) return 0L

            if (action == TransactionMode.SIMULATE && simulateExtractAlways != null) {
                return minOf(amount, simulateExtractAlways).coerceAtLeast(0L)
            }

            if (action == TransactionMode.EXECUTE && executeExtractAlways != null) {
                val out = minOf(amount, executeExtractAlways).coerceAtLeast(0L)
                if (out <= 0L) return 0L
                this.amount = (this.amount - out).coerceAtLeast(0L)
                return out
            }

            val extracted = minOf(amount, this.amount)
            if (extracted <= 0L) return 0L

            if (action == TransactionMode.EXECUTE) {
                this.amount -= extracted
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
