package github.kasuminova.prototypemachinery.impl.machine.component.system

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
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeExecutor
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.StructureComponentMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.type.FactoryRecipeProcessorComponentType
import github.kasuminova.prototypemachinery.impl.recipe.RecipeManagerImpl
import github.kasuminova.prototypemachinery.impl.recipe.index.RecipeIndexRegistry
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FactoryRecipeScanningWiringTest {

    @Test
    fun `processor component type system runs scanning and starts one process`() {
        val snapshot = RecipeManagerImpl.snapshotForTests()
        try {
            RecipeManagerImpl.clearForTests()

            // Initialize the recipe index registry for testing
            // This ensures the scanning system can use the index safely
            RecipeIndexRegistry.initializeForTests()

            val group = ResourceLocation("test", "group")

            RecipeManagerImpl.register(DummyRecipe(id = "r1", recipeGroups = setOf(group)))
            RecipeManagerImpl.register(DummyRecipe(id = "r2", recipeGroups = setOf(group)))

            val machine = DummyMachineInstance(formed = true, recipeGroups = setOf(group))
            val processor = DummyProcessorComponent(machine)

            for (sys in FactoryRecipeProcessorComponentType.systems) {
                sys.onTick(machine, processor)
            }

            assertEquals(1, processor.activeProcesses.size, "scanning should start at most one process per tick")
            val only = processor.activeProcesses.first()
            assertEquals("r1", only.recipe.id, "should follow recipe iteration order (insertion order)")
        } finally {
            RecipeManagerImpl.restoreForTests(snapshot)
        }
    }

    private class DummyRecipe(
        override val id: String,
        override val recipeGroups: Set<ResourceLocation>,
        override val durationTicks: Int = 10,
        override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>> = emptyMap(),
    ) : MachineRecipe

    private class DummyProcessorComponent(
        override val owner: MachineInstance,
    ) : FactoryRecipeProcessorComponent {

        override val provider: Any? = null

        override val type: MachineComponentType<*> = object : MachineComponentType<FactoryRecipeProcessorComponent> {
            override val id: ResourceLocation = ResourceLocation("test", "processor")
            override val system: MachineSystem<FactoryRecipeProcessorComponent>? = null
            override fun createComponent(machine: MachineInstance): FactoryRecipeProcessorComponent = error("unused")
        }

        override val activeProcesses: MutableCollection<RecipeProcess> = ArrayList()

        override val maxConcurrentProcesses: Int = 1

        override val executors: MutableList<RecipeExecutor> = ArrayList()

        override fun startProcess(process: RecipeProcess): Boolean {
            return activeProcesses.add(process)
        }

        override fun stopProcess(process: RecipeProcess) {
            activeProcesses.remove(process)
        }

        override fun tickProcesses() {}
    }

    private class DummyMachineInstance(
        private val formed: Boolean,
        private val recipeGroups: Set<ResourceLocation>,
    ) : MachineInstance {

        override val type: MachineType = object : MachineType {
            override val id: ResourceLocation = ResourceLocation("test", "dummy")
            override val name: String = "dummy"
            override val recipeGroups: Set<ResourceLocation> = this@DummyMachineInstance.recipeGroups
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

        override val componentMap: MachineComponentMap = object : MachineComponentMap {
            override val components: Map<MachineComponentType<*>, MachineComponent> = emptyMap()
            override val systems: List<MachineSystem<*>> = emptyList()
            override val orderedComponents = emptyList<github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentNode<MachineComponentType<*>, MachineComponent>>()
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

        override val structureComponentMap: StructureComponentMap = StructureComponentMapImpl()

        override val attributeMap: MachineAttributeMap = MachineAttributeMapImpl()

        override fun isFormed(): Boolean = formed

        override fun syncComponent(component: MachineComponent.Synchronizable) {
            assertTrue(true)
        }
    }
}
