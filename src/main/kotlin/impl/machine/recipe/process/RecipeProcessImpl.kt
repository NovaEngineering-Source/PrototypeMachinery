package github.kasuminova.prototypemachinery.impl.machine.recipe.process

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentMap
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.machine.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.machine.recipe.process.RecipeProcessStatus
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.machine.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.impl.ecs.TopologicalComponentMapImpl
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

    override val components: TopologicalComponentMap<RecipeProcessComponentType<*>, RecipeProcessComponent> = TopologicalComponentMapImpl()

    override fun serializeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()
        nbt.setTag("Status", NBTTagCompound().apply {
            setFloat("Progress", status.progress)
            setString("Message", status.message)
            setBoolean("IsError", status.isError)
        })
        
        val componentsTag = NBTTagCompound()
        components.orderedComponents.forEach { node ->
            val component = node.component
            componentsTag.setTag(component.type.id.toString(), component.serializeNBT())
        }
        nbt.setTag("Components", componentsTag)
        
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
        
        if (nbt.hasKey("Components")) {
            val componentsTag = nbt.getCompoundTag("Components")
            components.orderedComponents.forEach { node ->
                val component = node.component
                val key = component.type.id.toString()
                if (componentsTag.hasKey(key)) {
                    component.deserializeNBT(componentsTag.getCompoundTag(key))
                }
            }
        }
    }
}
