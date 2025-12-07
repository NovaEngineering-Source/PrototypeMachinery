package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeExecutor
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.impl.machine.recipe.RecipeManagerImpl
import github.kasuminova.prototypemachinery.impl.machine.recipe.process.RecipeProcessImpl
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants

public class FactoryRecipeProcessorComponentImpl(
    override val type: MachineComponentType<*>,
    override val owner: MachineInstance,
    override val provider: Any? = null
) : FactoryRecipeProcessorComponent, MachineComponent.Serializable {

    override val activeProcesses: MutableCollection<RecipeProcess> = ArrayList()

    override val maxConcurrentProcesses: Int
        get() = owner.attributeMap.attributes[StandardMachineAttributes.MAX_CONCURRENT_PROCESSES]?.value?.toInt() ?: 1

    override val executors: MutableList<RecipeExecutor> = ArrayList()

    override fun startProcess(process: RecipeProcess): Boolean {
        if (activeProcesses.size >= maxConcurrentProcesses) return false
        activeProcesses.add(process)
        return true
    }

    override fun stopProcess(process: RecipeProcess) {
        activeProcesses.remove(process)
    }

    override fun tickProcesses() {
        executors.forEach { it.tick(this) }
    }

    override fun writeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()
        nbt.setInteger("MaxConcurrent", maxConcurrentProcesses)

        val statusTag = NBTTagCompound()
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

    override fun readNBT(nbt: NBTTagCompound) {
        if (nbt.hasKey("Processes")) {
            val processList = nbt.getTagList("Processes", Constants.NBT.TAG_COMPOUND)
            activeProcesses.clear()
            for (i in 0 until processList.tagCount()) {
                val processTag = processList.getCompoundTagAt(i)
                val recipeId = processTag.getString("RecipeID")
                val recipe = RecipeManagerImpl.get(recipeId)

                if (recipe != null) {
                    val process = RecipeProcessImpl(owner, recipe)
                    process.deserializeNBT(processTag)
                    activeProcesses.add(process)
                }
            }
        }
    }

}