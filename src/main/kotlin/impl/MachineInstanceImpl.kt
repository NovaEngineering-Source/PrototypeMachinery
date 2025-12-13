package github.kasuminova.prototypemachinery.impl

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeMap
import github.kasuminova.prototypemachinery.api.machine.component.AffinityKeyProvider
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode
import github.kasuminova.prototypemachinery.api.scheduler.ISchedulable
import github.kasuminova.prototypemachinery.api.scheduler.SchedulingAffinity
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.network.NetworkHandler
import github.kasuminova.prototypemachinery.common.network.PacketSyncMachine
import github.kasuminova.prototypemachinery.common.util.warnWithBlockEntity
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl
import github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeNbt
import github.kasuminova.prototypemachinery.impl.machine.component.MachineComponentMapImpl
import net.minecraft.nbt.NBTTagCompound
import net.minecraftforge.fml.common.network.NetworkRegistry

public class MachineInstanceImpl(
    override val blockEntity: BlockEntity,
    override val type: MachineType
) : MachineInstance, ISchedulable, SchedulingAffinity {

    override val componentMap: MachineComponentMapImpl = MachineComponentMapImpl()

    override val attributeMap: MachineAttributeMap = MachineAttributeMapImpl()

    @Volatile
    private var active: Boolean = true

    @Volatile
    private var formed: Boolean = false

    @Volatile
    private var cachedAffinityKeys: Set<Any> = emptySet()

    @Volatile
    private var cachedAffinityModCount: Int = -1

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

    // region ISchedulable / Affinity implementation

    override fun onSchedule() {
        runCatching {
            val entries = componentMap.orderedTickEntries()

            runPhase("PreTick", entries) { it.system.onPreTick(this, it.component) }
            runPhase("Tick", entries) { it.system.onTick(this, it.component) }
            runPhase("PostTick", entries) { it.system.onPostTick(this, it.component) }
        }.onFailure {
            PrototypeMachinery.logger.warnWithBlockEntity(
                "Error occurred when ticking machine instance: machine `${type.id}`",
                blockEntity,
                it
            )
        }
    }

    private inline fun runPhase(
        phase: String,
        entries: Array<MachineComponentMapImpl.OrderedTickEntry>,
        action: (MachineComponentMapImpl.OrderedTickEntry) -> Unit
    ) {
        for (entry in entries) {
            runCatching {
                action(entry)
            }.onFailure { e ->
                PrototypeMachinery.logger.warnWithBlockEntity(
                    "Error occurred during machine $phase: machine `${type.id}`, component `${entry.component.type.id}`, system `${entry.system.javaClass.simpleName}`",
                    blockEntity,
                    e
                )
            }
        }
    }

    override fun getExecutionMode(): ExecutionMode {
        // Default to concurrent execution for better performance
        // 默认使用并发执行以获得更好的性能
        return ExecutionMode.CONCURRENT
    }

    override fun getSchedulingAffinityKeys(): Set<Any> {
        // 基于共享 IO 设备分组。
        // 由组件自行提供 affinity key。

        val modCount = componentMap.modificationCount
        if (modCount == cachedAffinityModCount) {
            return cachedAffinityKeys
        }

        val keys = mutableSetOf<Any>()
        for (component in componentMap.components.values) {
            (component as? AffinityKeyProvider)?.getAffinityKeys()?.let(keys::addAll)
        }

        cachedAffinityKeys = keys
        cachedAffinityModCount = modCount
        return keys
    }

    /**
     * Force invalidation of cached affinity keys.
     *
     * Components with dynamic affinity keys may call this when their key set changes.
     */
    internal fun invalidateSchedulingAffinityKeysCache() {
        cachedAffinityModCount = -1
    }

    // endregion

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