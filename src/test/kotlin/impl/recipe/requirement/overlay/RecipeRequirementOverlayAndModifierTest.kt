package github.kasuminova.prototypemachinery.impl.recipe.requirement.overlay

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
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementTypes
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.StructureComponentMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.modifier.RecipeOverlayModifier
import github.kasuminova.prototypemachinery.impl.recipe.modifier.RecipeOverlayModifierPipeline
import github.kasuminova.prototypemachinery.impl.recipe.modifier.RecipeOverlayModifierRegistry
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.EnergyRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.FluidRequirementComponent
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import net.minecraft.init.Bootstrap
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class RecipeRequirementOverlayAndModifierTest {

    companion object {
        private lateinit var fluidA: Fluid

        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            // FluidStack / ItemStack NBT may touch registries that expect Bootstrap.
            if (!Bootstrap.isRegistered()) {
                Bootstrap.register()
            }

            fluidA = Fluid("test_fluid_overlay_a", ResourceLocation("test", "still"), ResourceLocation("test", "flow"))
            if (!FluidRegistry.isFluidRegistered(fluidA)) {
                FluidRegistry.registerFluid(fluidA)
            }
        }
    }

    @AfterEach
    fun cleanupRegistry() {
        // Avoid cross-test pollution.
        RecipeOverlayModifierRegistry.clear()
    }

    @Test
    fun `overlay resolves energy per tick and ignore_output_full`() {
        val machine = DummyMachineInstance()
        val process = dummyProcess(machine)

        val base = EnergyRequirementComponent(
            id = "e",
            inputPerTick = 1L,
            outputPerTick = 2L,
            properties = emptyMap(),
        )

        val overlay = RecipeRequirementOverlay.getOrCreate(process)
        overlay.setEnergyPerTick(componentId = "e", inputPerTick = 30L, outputPerTick = 40L)
        overlay.setIgnoreOutputFull(RecipeRequirementTypes.ENERGY, componentId = "e", value = true)

        val effective = RecipeRequirementOverlay.resolve(process, base)
        assertNotSame(base, effective)
        assertEquals(30L, effective.inputPerTick)
        assertEquals(40L, effective.outputPerTick)
        assertEquals(true, effective.properties["ignore_output_full"])

        // Base must remain unchanged
        assertEquals(1L, base.inputPerTick)
        assertEquals(2L, base.outputPerTick)
        assertTrue(base.properties.isEmpty())
    }

    @Test
    fun `overlay nbt roundtrip applies to fluid and item components`() {
        val machine = DummyMachineInstance()

        val process1 = dummyProcess(machine)
        val overlay1 = RecipeRequirementOverlay.getOrCreate(process1)

        overlay1.setFluidIO(
            componentId = "f",
            inputsPerTick = listOf(fluidKey(100L)),
            outputsPerTick = listOf(fluidKey(200L)),
        )
        overlay1.setItemIO(
            componentId = "i",
            inputs = listOf(itemKey(7L)),
            outputs = listOf(itemKey(9L)),
        )

        val nbt: NBTTagCompound = overlay1.serializeNBT()

        val process2 = dummyProcess(machine)
        val overlay2 = RecipeRequirementOverlay.getOrCreate(process2)
        overlay2.deserializeNBT(nbt)

        val baseFluid = FluidRequirementComponent(
            id = "f",
            inputsPerTick = listOf(fluidKey(1L)),
            outputsPerTick = listOf(fluidKey(2L)),
        )
        val effectiveFluid = RecipeRequirementOverlay.resolve(process2, baseFluid)
        assertEquals(100L, effectiveFluid.inputsPerTick.single().count)
        assertEquals(200L, effectiveFluid.outputsPerTick.single().count)

        val baseItem = ItemRequirementComponent(
            id = "i",
            inputs = listOf(itemKey(1L)),
            outputs = listOf(itemKey(2L)),
        )
        val effectiveItem = RecipeRequirementOverlay.resolve(process2, baseItem)
        assertEquals(7L, effectiveItem.inputs.single().count)
        assertEquals(9L, effectiveItem.outputs.single().count)
    }

    @Test
    fun `modifier pipeline writes overlay and affects resolve`() {
        val machine = DummyMachineInstance()
        val process = dummyProcess(machine)

        val modifierId = "test:overlay_modifier"
        RecipeOverlayModifierRegistry.register(object : RecipeOverlayModifier {
            override val id: String = modifierId

            override fun apply(ctx: github.kasuminova.prototypemachinery.impl.recipe.modifier.RecipeOverlayModifierContext) {
                ctx.setEnergyPerTick(componentId = "e", inputPerTick = 123L, outputPerTick = 0L)
                ctx.setIgnoreOutputFull(RecipeRequirementTypes.ENERGY, componentId = "e", value = true)
            }
        })

        RecipeOverlayModifierPipeline.apply(machine, process, listOf(modifierId))

        val base = EnergyRequirementComponent(id = "e", inputPerTick = 1L)
        val effective = RecipeRequirementOverlay.resolve(process, base)

        assertEquals(123L, effective.inputPerTick)
        assertEquals(true, effective.properties["ignore_output_full"])
    }

    private fun itemKey(amount: Long): github.kasuminova.prototypemachinery.api.key.PMKey<ItemStack> {
        val stack = ItemStack(Items.DIAMOND, amount.toInt().coerceAtLeast(1))
        return PMItemKeyType.create(stack).apply { count = amount }
    }

    private fun fluidKey(amount: Long): github.kasuminova.prototypemachinery.api.key.PMKey<FluidStack> {
        // Keep within Int for FluidStack
        val stack = FluidStack(fluidA, amount.toInt().coerceAtLeast(1))
        return PMFluidKeyType.create(stack).apply { count = amount }
    }

    private fun dummyProcess(machine: MachineInstance): RecipeProcess {
        val recipe = object : MachineRecipe {
            override val id: String = "dummy"
            override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>> = emptyMap()
        }
        return RecipeProcessImpl(machine, recipe, seed = 42L)
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
