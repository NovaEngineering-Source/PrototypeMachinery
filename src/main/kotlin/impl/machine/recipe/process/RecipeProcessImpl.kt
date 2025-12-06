package github.kasuminova.prototypemachinery.impl.machine.recipe.process

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.machine.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcessStatus
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponentType
import net.minecraft.nbt.NBTTagCompound

public class RecipeProcessImpl(
    override val owner: MachineInstance,
    override val recipe: MachineRecipe
) : RecipeProcess {

    override val attributeMap: MachineAttributeMap = object : MachineAttributeMap {
        override val attributes: MutableMap<MachineAttributeType, MachineAttributeInstance> = mutableMapOf()
    }

    override var status: RecipeProcessStatus = RecipeProcessStatus(
        progress = 0.0f,
        message = "Processing",
        isError = false
    )

    override val components: MutableMap<RecipeProcessComponentType<*>, RecipeProcessComponent> = mutableMapOf()

    override fun serializeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()
        nbt.setTag("Status", NBTTagCompound().apply {
            setFloat("Progress", status.progress)
            setString("Message", status.message)
            setBoolean("IsError", status.isError)
        })
        // Component serialization would go here
        return nbt
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        if (nbt.hasKey("Status")) {
            val statusNbt = nbt.getCompoundTag("Status")
            status = RecipeProcessStatus(
                progress = statusNbt.getFloat("Progress"),
                message = statusNbt.getString("Message"),
                isError = statusNbt.getBoolean("IsError")
            )
        }
        // Component deserialization would go here
    }
}
