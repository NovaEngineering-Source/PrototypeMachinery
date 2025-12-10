package github.kasuminova.prototypemachinery.impl

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode
import github.kasuminova.prototypemachinery.api.scheduler.ISchedulable
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.MachineComponentMapImpl
import net.minecraft.nbt.NBTTagCompound

public class MachineInstanceImpl(
    override val blockEntity: BlockEntity,
    override val type: MachineType
) : MachineInstance, ISchedulable {

    override val componentMap: MachineComponentMapImpl = MachineComponentMapImpl()

    override val attributeMap: MachineAttributeMap = MachineAttributeMapImpl()

    @Volatile
    private var active: Boolean = true

    @Volatile
    private var formed: Boolean = false

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

    // ISchedulable implementation
    // ISchedulable 实现

    override fun onSchedule() {
        // Execute machine logic here
        // 在此执行机械逻辑
        
        // TODO: Implement actual machine logic execution
        // TODO: 实现实际的机械逻辑执行
    }

    override fun getExecutionMode(): ExecutionMode {
        // Default to concurrent execution for better performance
        // 默认使用并发执行以获得更好的性能
        return ExecutionMode.CONCURRENT
    }

    override fun isActive(): Boolean {
        return active && !blockEntity.isInvalid
    }

    /**
     * Mark this machine instance as inactive.
     * 将此机械实例标记为非活动状态。
     *
     * Should be called when the machine is being unloaded.
     * 应在机械被卸载时调用。
     */
    internal fun setInactive() {
        active = false
    }

    override fun isFormed(): Boolean {
        return formed
    }

    /**
     * Set the formed state of this machine.
     * 设置此机械的形成状态。
     *
     * @param formed true if the structure is valid and formed, false otherwise
     */
    internal fun setFormed(formed: Boolean) {
        this.formed = formed
    }

}