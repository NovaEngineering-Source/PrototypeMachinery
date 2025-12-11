package github.kasuminova.prototypemachinery.api.machine.component.base

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponent.Synchronizable
import net.minecraft.nbt.NBTTagCompound

/**
 * Interface for synchronizable components with standard implementation.
 * 带有标准实现的可同步组件接口。
 * 
 * Provides a structured way to handle full vs incremental syncs without forcing inheritance.
 * 提供了一种结构化的方式来处理全量与增量同步，而无需强制继承。
 */
public interface SynchronizableComponent : Synchronizable {

    /**
     * Trigger a network sync for this component.
     * 触发此组件的网络同步。
     * 
     * Should be called after modifying data that needs to be synced to client.
     * 应在修改需要同步到客户端的数据后调用。
     */
    public fun sync() {
        owner.syncComponent(this)
    }

    override fun writeClientNBT(type: Synchronizable.SyncType): NBTTagCompound? {
        val tag = NBTTagCompound()
        if (type == Synchronizable.SyncType.FULL) {
            writeFullSyncData(tag)
        } else {
            if (!writeIncrementalSyncData(tag)) {
                return null // No data to sync / 无需同步
            }
        }
        return if (tag.isEmpty) null else tag
    }

    override fun readClientNBT(nbt: NBTTagCompound, type: Synchronizable.SyncType) {
        if (type == Synchronizable.SyncType.FULL) {
            readFullSyncData(nbt)
        } else {
            readIncrementalSyncData(nbt)
        }
    }

    /**
     * Write all data required for client initialization (e.g. GUI open, chunk load).
     * 写入客户端初始化所需的所有数据（例如 GUI 打开、区块加载）。
     * 
     * Default implementation checks if component is [MachineComponent.Serializable] and uses [MachineComponent.Serializable.writeNBT].
     * 默认实现检查组件是否为 [MachineComponent.Serializable] 并使用 [MachineComponent.Serializable.writeNBT]。
     * 
     * @param nbt The tag to write to / 写入的目标标签
     */
    public fun writeFullSyncData(nbt: NBTTagCompound) {
        if (this is MachineComponent.Serializable) {
            nbt.merge(this.writeNBT())
        }
    }

    /**
     * Write only changed data. Return true if any data was written.
     * 仅写入变更的数据。如果有数据写入则返回 true。
     * 
     * **Important**: Implementations MUST check internal dirty flags here and clear them.
     * **重要**: 实现类必须在此处检查内部脏标记并清除它们。
     * 
     * @param nbt The tag to write to / 写入的目标标签
     * @return true if data was written, false otherwise / 如果写入了数据返回 true，否则返回 false
     */
    public fun writeIncrementalSyncData(nbt: NBTTagCompound): Boolean

    /**
     * Read full sync data received from server.
     * 读取从服务端接收的全量同步数据。
     * 
     * Default implementation checks if component is [MachineComponent.Serializable] and uses [MachineComponent.Serializable.readNBT].
     * 默认实现检查组件是否为 [MachineComponent.Serializable] 并使用 [MachineComponent.Serializable.readNBT]。
     */
    public fun readFullSyncData(nbt: NBTTagCompound) {
        if (this is MachineComponent.Serializable) {
            this.readNBT(nbt)
        }
    }

    /**
     * Read incremental sync data received from server.
     * 读取从服务端接收的增量同步数据。
     * 
     * Implementations should check for existence of keys to know what updated.
     * 实现类应检查键是否存在以获知更新了什么。
     */
    public fun readIncrementalSyncData(nbt: NBTTagCompound)

}
