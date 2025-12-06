package github.kasuminova.prototypemachinery.impl.machine.component.type

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.RecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeExecutor
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants

public class RecipeProcessorComponentImpl(
    override val type: MachineComponentType<*>,
    override val owner: MachineInstance,
    override val provider: Any? = null
) : RecipeProcessorComponent {

    override val activeProcesses: MutableCollection<RecipeProcess> = ArrayList()

    override var maxConcurrentProcesses: Int
        get() = owner.attributeMap.attributes[StandardMachineAttributes.MAX_CONCURRENT_PROCESSES]?.value?.toInt() ?: 1
        set(value) {
            owner.attributeMap.attributes[StandardMachineAttributes.MAX_CONCURRENT_PROCESSES]?.base = value.toDouble()
        }

    override var status: RecipeProcessorComponent.ProcessorStatus =
        RecipeProcessorComponent.ProcessorStatus(RecipeProcessorComponent.ProcessorStatus.StatusType.IDLE)
    override val executors: MutableList<RecipeExecutor> = ArrayList()

    override fun startProcess(process: RecipeProcess): Boolean {
        if (activeProcesses.size >= maxConcurrentProcesses) return false
        activeProcesses.add(process)
        status = RecipeProcessorComponent.ProcessorStatus(RecipeProcessorComponent.ProcessorStatus.StatusType.PROCESSING)
        return true
    }

    override fun stopProcess(process: RecipeProcess) {
        activeProcesses.remove(process)
        if (activeProcesses.isEmpty()) {
            status = RecipeProcessorComponent.ProcessorStatus(RecipeProcessorComponent.ProcessorStatus.StatusType.IDLE)
        }
    }

    override fun tickProcesses() {
        executors.forEach { it.tick(this) }
    }

    override fun serializeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()
        nbt.setInteger("MaxConcurrent", maxConcurrentProcesses)

        val statusTag = NBTTagCompound()
        statusTag.setString("Type", status.type.name)
        statusTag.setString("Message", status.message)
        nbt.setTag("Status", statusTag)

        val processList = NBTTagList()
        activeProcesses.forEach { process ->
            val processTag = process.serializeNBT()
            processTag.setString("RecipeID", process.recipe.id)
            processList.appendTag(processTag)
        }
        nbt.setTag("Processes", processList)

        return nbt
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        if (nbt.hasKey("MaxConcurrent")) {
            maxConcurrentProcesses = nbt.getInteger("MaxConcurrent")
        } else if (nbt.hasKey("MaxParallel")) {
            maxConcurrentProcesses = nbt.getInteger("MaxParallel")
        }

        if (nbt.hasKey("Status")) {
            val statusTag = nbt.getCompoundTag("Status")
            val typeName = statusTag.getString("Type")
            val message = statusTag.getString("Message")
            val type = try {
                RecipeProcessorComponent.ProcessorStatus.StatusType.valueOf(typeName)
            } catch (e: IllegalArgumentException) {
                RecipeProcessorComponent.ProcessorStatus.StatusType.IDLE
            }
            status = RecipeProcessorComponent.ProcessorStatus(type, message)
        }

        if (nbt.hasKey("Processes")) {
            val processList = nbt.getTagList("Processes", Constants.NBT.TAG_COMPOUND)
            activeProcesses.clear()
            for (i in 0 until processList.tagCount()) {
                val processTag = processList.getCompoundTagAt(i)
                val recipeId = processTag.getString("RecipeID")
                val recipe = github.kasuminova.prototypemachinery.impl.machine.recipe.RecipeManagerImpl.get(recipeId)

                if (recipe != null) {
                    val process = github.kasuminova.prototypemachinery.impl.machine.recipe.process.RecipeProcessImpl(owner, recipe)
                    process.deserializeNBT(processTag)
                    activeProcesses.add(process)
                }
            }
        }
    }
}
