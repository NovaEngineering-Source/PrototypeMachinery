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
import github.kasuminova.prototypemachinery.api.machine.component.container.EnumerableItemKeyContainer
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureItemKeyContainer
import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.logic.StructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.process.ProcessResult
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.requirement.RecipeRequirementType
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.DynamicItemInputGroup
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.ItemRequirementMatcherRegistry
import github.kasuminova.prototypemachinery.api.recipe.requirement.advanced.RequirementPropertyKeys
import github.kasuminova.prototypemachinery.api.recipe.requirement.component.RecipeRequirementComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.StructureComponentMapImpl
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import github.kasuminova.prototypemachinery.impl.recipe.requirement.ItemRequirementComponent
import net.minecraft.init.Bootstrap
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ItemRequirementSystemDynamicMatcherTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun bootstrapMinecraft() {
            if (!Bootstrap.isRegistered()) {
                Bootstrap.register()
            }
        }
    }

    @Test
    fun `dynamic input selects matching variant and consumes`() {
        // matcher: item must be same; if pattern has tag Foo, candidate must have same Foo.
        ItemRequirementMatcherRegistry.register("foo_eq") { cand, pat ->
            if (cand.isEmpty || pat.isEmpty) return@register false
            if (cand.item !== pat.item || cand.metadata != pat.metadata) return@register false

            val pt = pat.tagCompound ?: return@register true
            val ct = cand.tagCompound ?: return@register false
            if (!pt.hasKey("Foo")) return@register true
            ct.hasKey("Foo") && ct.getInteger("Foo") == pt.getInteger("Foo")
        }

        val machine = DummyMachineInstance()
        val src = DummyItemContainer(machine, allowed = setOf(PortMode.OUTPUT))
        machine.structureComponentMap.add(src)

        val pattern = ItemStack(Items.DIAMOND, 1).apply {
            tagCompound = NBTTagCompound().apply { setInteger("Foo", 1) }
        }
        val candOk = ItemStack(Items.DIAMOND, 1).apply {
            tagCompound = NBTTagCompound().apply { setInteger("Foo", 1) }
        }
        val candBad = ItemStack(Items.DIAMOND, 1).apply {
            tagCompound = NBTTagCompound().apply { setInteger("Foo", 2) }
        }

        // Put only the matching variant into the container.
        src.put(PMItemKeyType.create(candOk), 10)
        src.put(PMItemKeyType.create(candBad), 10)

        val process = dummyProcess(machine)
        val group = DynamicItemInputGroup(
            matcherId = "foo_eq",
            pattern = PMItemKeyType.create(pattern),
            count = 5,
            displayedCandidates = listOf(PMItemKeyType.create(pattern)),
            maxCandidates = 64,
        )
        val component = ItemRequirementComponent(
            id = "i",
            properties = mapOf(RequirementPropertyKeys.DYNAMIC_ITEM_INPUTS to listOf(group))
        )

        val tx = ItemRequirementSystem.start(process, component)
        assertEquals(ProcessResult.Success, tx.result)
        tx.commit()

        // Must consume 5 from the matching variant; the non-matching one is untouched.
        assertEquals(5L, src.getAmount(PMItemKeyType.create(candOk)))
        assertEquals(10L, src.getAmount(PMItemKeyType.create(candBad)))
    }

    @Test
    fun `dynamic input blocked when no candidate matches`() {
        ItemRequirementMatcherRegistry.register("always_false") { _, _ -> false }

        val machine = DummyMachineInstance()
        val src = DummyItemContainer(machine, allowed = setOf(PortMode.OUTPUT))
        machine.structureComponentMap.add(src)

        src.put(PMItemKeyType.create(ItemStack(Items.DIAMOND, 1)), 10)

        val process = dummyProcess(machine)
        val group = DynamicItemInputGroup(
            matcherId = "always_false",
            pattern = PMItemKeyType.create(ItemStack(Items.DIAMOND, 1)),
            count = 1,
            displayedCandidates = emptyList(),
            maxCandidates = 64,
        )
        val component = ItemRequirementComponent(
            id = "i",
            properties = mapOf(RequirementPropertyKeys.DYNAMIC_ITEM_INPUTS to listOf(group))
        )

        val tx = ItemRequirementSystem.start(process, component)
        assertTrue(tx.result is ProcessResult.Blocked)
        tx.commit()

        // No side effects.
        assertEquals(10L, src.getAmount(PMItemKeyType.create(ItemStack(Items.DIAMOND, 1))))
    }

    private fun dummyProcess(machine: MachineInstance): RecipeProcess {
        val recipe = object : MachineRecipe {
            override val id: String = "dummy"
            override val requirements: Map<RecipeRequirementType<*>, List<RecipeRequirementComponent>> = emptyMap()
        }
        return RecipeProcessImpl(machine, recipe, seed = 42L)
    }

    private class DummyItemContainer(
        override val owner: MachineInstance,
        override val provider: Any? = null,
        private val allowed: Set<PortMode>,
    ) : StructureItemKeyContainer, EnumerableItemKeyContainer {

        private val totals: LinkedHashMap<PMKey<ItemStack>, Long> = LinkedHashMap()

        override fun isAllowedPortMode(mode: PortMode): Boolean = allowed.contains(mode)

        fun put(key: PMKey<ItemStack>, amount: Long) {
            if (amount <= 0L) return
            totals[key] = (totals[key] ?: 0L) + amount
        }

        fun getAmount(key: PMKey<ItemStack>): Long {
            return totals.entries.firstOrNull { it.key == key }?.value ?: 0L
        }

        override fun getAllKeysSnapshot(): Collection<PMKey<ItemStack>> {
            return totals.filterValues { it > 0L }.keys.toList()
        }

        override fun insert(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
            if (amount <= 0L) return 0L
            if (!isAllowedPortMode(PortMode.INPUT)) return 0L
            return insertUnchecked(key, amount, mode)
        }

        override fun insertUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
            if (amount <= 0L) return 0L
            if (mode == TransactionMode.EXECUTE) {
                put(key, amount)
            }
            return amount
        }

        override fun extract(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
            if (amount <= 0L) return 0L
            if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L
            return extractUnchecked(key, amount, mode)
        }

        override fun extractUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
            if (amount <= 0L) return 0L

            val entry = totals.entries.firstOrNull { it.key == key } ?: return 0L
            val have = entry.value
            val got = minOf(amount, have).coerceAtLeast(0L)
            if (got <= 0L) return 0L

            if (mode == TransactionMode.EXECUTE) {
                val left = have - got
                if (left <= 0L) totals.remove(entry.key) else totals[entry.key] = left
            }
            return got
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
