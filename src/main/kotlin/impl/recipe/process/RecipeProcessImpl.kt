package github.kasuminova.prototypemachinery.impl.recipe.process

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentMap
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.recipe.MachineRecipe
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcessStatus
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.impl.ecs.TopologicalComponentMapImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeNbt
import github.kasuminova.prototypemachinery.impl.machine.attribute.OverlayMachineAttributeMapImpl
import net.minecraft.nbt.NBTTagCompound
import java.util.concurrent.ThreadLocalRandom

public class RecipeProcessImpl(
    override val owner: MachineInstance,
    override val recipe: MachineRecipe,
    override var seed: Long = ThreadLocalRandom.current().nextLong()
) : RecipeProcess {

    override val attributeMap: MachineAttributeMap = OverlayMachineAttributeMapImpl(
        parent = owner.attributeMap,
        defaultBase = 1.0,
    )

    override var status: RecipeProcessStatus = RecipeProcessStatus(
        progress = 0.0f,
        message = "Processing",
        isError = false
    )

    override val components: TopologicalComponentMap<RecipeProcessComponentType<*>, RecipeProcessComponent> = TopologicalComponentMapImpl()

    override fun serializeNBT(): NBTTagCompound {
        val nbt = NBTTagCompound()
        nbt.setLong("Seed", seed)
        nbt.setTag("Status", NBTTagCompound().apply {
            setFloat("Progress", status.progress)
            setString("Message", status.message)
            setBoolean("IsError", status.isError)
        })

        (attributeMap as? OverlayMachineAttributeMapImpl)?.let { overlay ->
            nbt.setTag("Attributes", MachineAttributeNbt.writeOverlayLocal(overlay))
        }

        val componentsTag = NBTTagCompound()
        components.orderedComponents.forEach { node ->
            val component = node.component
            componentsTag.setTag(component.type.id.toString(), component.serializeNBT())
        }
        nbt.setTag("Components", componentsTag)

        return nbt
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        if (nbt.hasKey("Seed")) {
            seed = nbt.getLong("Seed")
        }
        if (nbt.hasKey("Status")) {
            val statusNbt = nbt.getCompoundTag("Status")
            status = RecipeProcessStatus(
                progress = statusNbt.getFloat("Progress"),
                message = statusNbt.getString("Message"),
                isError = statusNbt.getBoolean("IsError")
            )
        }

        if (nbt.hasKey("Attributes")) {
            val attrTag = nbt.getCompoundTag("Attributes")
            val overlay = attributeMap as? OverlayMachineAttributeMapImpl
            if (overlay != null) {
                MachineAttributeNbt.readOverlayLocal(attrTag, overlay)
            }
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
