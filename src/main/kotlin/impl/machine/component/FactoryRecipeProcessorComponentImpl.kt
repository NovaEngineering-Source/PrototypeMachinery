package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.machine.workslot.WorkSlot
import github.kasuminova.prototypemachinery.api.machine.workslot.WorkSlotDefinition
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeExecutor
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.impl.recipe.RecipeManagerImpl
import github.kasuminova.prototypemachinery.impl.recipe.process.RecipeProcessImpl
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.util.ResourceLocation
import net.minecraftforge.common.util.Constants

public class FactoryRecipeProcessorComponentImpl(
    override val type: MachineComponentType<*>,
    override val owner: MachineInstance,
    override val provider: Any? = null
) : FactoryRecipeProcessorComponent, MachineComponent.Serializable {

    private fun effectiveCapacity(): Int = minOf(maxConcurrentProcesses, workSlots.size)

    private inner class ActiveProcessCollection : MutableCollection<RecipeProcess> {

        override val size: Int
            get() = workSlots.count { it.process != null }

        private fun firstFreeSlot(): WorkSlot? = workSlots.firstOrNull { it.process == null }

        private fun findSlot(process: RecipeProcess): WorkSlot? = workSlots.firstOrNull { it.process === process }

        override fun add(element: RecipeProcess): Boolean {
            if (size >= effectiveCapacity()) return false
            val slot = firstFreeSlot() ?: return false
            slot.process = element
            return true
        }

        override fun addAll(elements: Collection<RecipeProcess>): Boolean {
            var changed = false
            for (e in elements) {
                changed = add(e) || changed
            }
            return changed
        }

        override fun clear() {
            workSlots.forEach { it.process = null }
        }

        override fun iterator(): MutableIterator<RecipeProcess> {
            val snapshot = workSlots.toList()
            var idx = 0
            var lastSlot: WorkSlot? = null

            return object : MutableIterator<RecipeProcess> {
                override fun hasNext(): Boolean {
                    while (idx < snapshot.size) {
                        val p = snapshot[idx].process
                        if (p != null) return true
                        idx++
                    }
                    return false
                }

                override fun next(): RecipeProcess {
                    while (idx < snapshot.size) {
                        val slot = snapshot[idx++]
                        val p = slot.process
                        if (p != null) {
                            lastSlot = slot
                            return p
                        }
                    }
                    throw NoSuchElementException()
                }

                override fun remove() {
                    val s = lastSlot ?: throw IllegalStateException("next() not called")
                    s.process = null
                    lastSlot = null
                }
            }
        }

        override fun contains(element: RecipeProcess): Boolean = findSlot(element) != null

        override fun containsAll(elements: Collection<RecipeProcess>): Boolean = elements.all { contains(it) }

        override fun isEmpty(): Boolean = size == 0

        override fun remove(element: RecipeProcess): Boolean {
            val slot = findSlot(element) ?: return false
            slot.process = null
            return true
        }

        override fun removeAll(elements: Collection<RecipeProcess>): Boolean {
            var changed = false
            for (e in elements) {
                changed = remove(e) || changed
            }
            return changed
        }

        override fun retainAll(elements: Collection<RecipeProcess>): Boolean {
            val keep = elements.toSet()
            var changed = false
            for (slot in workSlots) {
                val p = slot.process ?: continue
                if (p !in keep) {
                    slot.process = null
                    changed = true
                }
            }
            return changed
        }
    }

    private class WorkSlotImpl(
        override val name: String,
        override val allowedRecipeGroups: Set<ResourceLocation>,
    ) : WorkSlot {
        override var process: RecipeProcess? = null
        override var statusText: String? = null
    }

    private fun defaultWorkSlotDefinitions(): List<WorkSlotDefinition> {
        val defs = owner.type.workSlots
        if (defs.isNotEmpty()) return defs

        // Implicit single slot when machine type does not define work slots.
        // If recipeGroups is empty, treat it as "accept all".
        return listOf(
            WorkSlotDefinition(
                name = "main",
                allowedRecipeGroups = owner.type.recipeGroups
            )
        )
    }

    override val workSlots: MutableList<WorkSlot> = defaultWorkSlotDefinitions()
        .map { WorkSlotImpl(it.name, it.allowedRecipeGroups) }
        .toMutableList()

    override val activeProcesses: MutableCollection<RecipeProcess> = ActiveProcessCollection()

    override val maxConcurrentProcesses: Int
        get() = owner.attributeMap.attributes[StandardMachineAttributes.MAX_CONCURRENT_PROCESSES]?.value?.toInt() ?: 1

    override val executors: MutableList<RecipeExecutor> = ArrayList()

    override fun startProcess(process: RecipeProcess): Boolean {
        if (activeProcesses.size >= effectiveCapacity()) return false
        val slot = workSlots.firstOrNull { it.process == null } ?: return false
        return startProcessIn(slot, process)
    }

    override fun stopProcess(process: RecipeProcess) {
        activeProcesses.remove(process)
    }

    override fun startProcessIn(slot: WorkSlot, process: RecipeProcess): Boolean {
        if (slot !in workSlots) return false
        if (slot.process != null) return false
        if (activeProcesses.size >= effectiveCapacity()) return false
        slot.process = process
        return true
    }

    override fun tickProcesses() {
        executors.forEach { it.tick(this) }
    }

    override fun writeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()

        val slotList = NBTTagList()
        workSlots.forEach { slot ->
            val slotTag = NBTTagCompound()
            slotTag.setString("Name", slot.name)
            slot.statusText?.let { slotTag.setString("StatusText", it) }

            val process = slot.process
            if (process != null) {
                val processTag = process.serializeNBT()
                processTag.setString("RecipeID", process.recipe.id)
                slotTag.setTag("Process", processTag)
            }

            slotList.appendTag(slotTag)
        }
        nbt.setTag("WorkSlots", slotList)

        return nbt
    }

    override fun readNBT(nbt: NBTTagCompound) {
        // No backward compatibility: only WorkSlots is supported.
        workSlots.forEach {
            it.process = null
            it.statusText = null
        }

        if (!nbt.hasKey("WorkSlots", Constants.NBT.TAG_LIST)) return

        val slotList = nbt.getTagList("WorkSlots", Constants.NBT.TAG_COMPOUND)
        for (i in 0 until slotList.tagCount()) {
            val slotTag = slotList.getCompoundTagAt(i)
            val name = slotTag.getString("Name")
            val slot = workSlots.firstOrNull { it.name == name } ?: continue

            if (slotTag.hasKey("StatusText", Constants.NBT.TAG_STRING)) {
                slot.statusText = slotTag.getString("StatusText")
            }

            if (slotTag.hasKey("Process", Constants.NBT.TAG_COMPOUND)) {
                val processTag = slotTag.getCompoundTag("Process")
                val recipeId = processTag.getString("RecipeID")
                val recipe = RecipeManagerImpl.get(recipeId) ?: continue

                val process = RecipeProcessImpl(owner, recipe, seed = 0) // Seed will be overwritten in deserializeNBT
                process.deserializeNBT(processTag)
                slot.process = process
            }
        }
    }

}