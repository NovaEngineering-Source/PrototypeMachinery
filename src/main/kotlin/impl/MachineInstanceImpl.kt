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
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeNbt
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.component.MachineComponentMapImpl
import net.minecraft.nbt.NBTTagCompound

import github.kasuminova.prototypemachinery.common.network.NetworkHandler
import github.kasuminova.prototypemachinery.common.network.PacketSyncMachine
import net.minecraftforge.fml.common.network.NetworkRegistry

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

    /**
     * Request a sync for a specific component.
     * 请求同步特定组件。
     */
    override fun syncComponent(component: MachineComponent.Synchronizable) {
        if (blockEntity.world.isRemote) return

        val data = component.writeClientNBT(MachineComponent.Synchronizable.SyncType.INCREMENTAL) ?: return
        val pos = blockEntity.pos
        val packet = PacketSyncMachine(pos, component.type.id.toString(), data, false)
        
        // Send to all players tracking this chunk
        val target = NetworkRegistry.TargetPoint(
            blockEntity.world.provider.dimension,
            pos.x.toDouble(),
            pos.y.toDouble(),
            pos.z.toDouble(),
            -1.0, // sendToAllTracking does not require a specific range
        )
        NetworkHandler.INSTANCE.sendToAllTracking(packet, target)
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
        if (tag.hasKey("Attributes") && attributeMap is MachineAttributeMapImpl) {
            runCatching {
                MachineAttributeNbt.readMachineMap(tag.getCompoundTag("Attributes"), attributeMap as MachineAttributeMapImpl)
            }.onFailure {
                PrototypeMachinery.logger.warnWithBlockEntity("Error while reading machine attribute map.", blockEntity, it)
            }
        }

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
        if (attributeMap is MachineAttributeMapImpl) {
            runCatching {
                tag.setTag("Attributes", MachineAttributeNbt.writeMachineMap(attributeMap as MachineAttributeMapImpl))
            }.onFailure {
                PrototypeMachinery.logger.warnWithBlockEntity("Error while writing machine attribute map.", blockEntity, it)
            }
        }

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
        runCatching {
            // TODO Ticking Machine Components
        }.onFailure {
            PrototypeMachinery.logger.warnWithBlockEntity("Error occurred when ticking machine instance.", blockEntity, it)
        }
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