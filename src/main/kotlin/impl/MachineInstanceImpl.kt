package github.kasuminova.prototypemachinery.impl

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.MachineComponentMapImpl
import net.minecraft.nbt.NBTTagCompound

public class MachineInstanceImpl(
    override val blockEntity: BlockEntity,
    override val type: MachineType
) : MachineInstance {

    override val componentMap: MachineComponentMapImpl = MachineComponentMapImpl()

    override val attributeMap: MachineAttributeMap = MachineAttributeMapImpl()

    init {
        createComponents()
    }

    private fun createComponents() {
        type.componentTypes.forEach { componentType ->
            runCatching {
                val component = componentType.createComponent(this)
                componentMap.add(component)
            }.onFailure {
                PrototypeMachinery.logger.warnWithBlockEntity(
                    "Error while creating machine `${type.id}` component `${componentType.id}`",
                    blockEntity,
                    it
                )
            }
        }
    }

    internal fun readNBT(tag: NBTTagCompound) {
        componentMap.components.forEach { (type, component) ->
            if (component is MachineComponent.Serializable) {
                runCatching {
                    component.readNBT(tag.getCompoundTag(type.id.toString()))
                }.onFailure {
                    PrototypeMachinery.logger.warnWithBlockEntity("Error while reading machine component data `${type.id}`", blockEntity, it)
                }
            }
        }
    }

    internal fun writeNBT(tag: NBTTagCompound) {
        componentMap.components.forEach { (type, component) ->
            if (component is MachineComponent.Serializable) {
                runCatching {
                    tag.setTag(type.id.toString(), component.writeNBT())
                }.onFailure {
                    PrototypeMachinery.logger.warnWithBlockEntity("Error while writing machine component data `${type.id}`", blockEntity, it)
                }
            }
        }
    }

}