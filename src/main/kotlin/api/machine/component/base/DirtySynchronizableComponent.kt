package github.kasuminova.prototypemachinery.api.machine.component.base

import net.minecraft.nbt.NBTTagCompound

/**
 * A small helper base class for the most common sync pattern:
 * - maintain an internal dirty flag
 * - incremental sync sends "full" payload when dirty
 * - incremental read uses the same schema as full read
 *
 * 最常见同步模式的辅助基类：
 * - 内部 dirty 标记
 * - 增量同步：dirty 时发送“等同全量”的数据
 * - 增量读取：复用全量读取逻辑（同 schema）
 */
public abstract class DirtySynchronizableComponent : SynchronizableComponent {

    /**
     * Whether the component has changes that should be synced.
     * 是否存在需要同步到客户端的变更。
     */
    protected var dirty: Boolean = false
        private set

    /** Mark dirty and optionally trigger sync immediately. / 标记为 dirty，并可选立即触发 sync。 */
    protected fun markDirty(syncNow: Boolean = true) {
        dirty = true
        if (syncNow) {
            sync()
        }
    }

    /** Clear dirty flag. / 清除 dirty 标记。 */
    protected fun clearDirty() {
        dirty = false
    }

    /**
     * Default incremental write: if dirty, clear dirty and write full sync payload.
     * 默认增量写入：dirty 时清除 dirty 并写入全量同步负载。
     */
    final override fun writeIncrementalSyncData(nbt: NBTTagCompound): Boolean {
        if (!dirty) return false
        clearDirty()
        writeFullSyncData(nbt)
        return !nbt.isEmpty
    }

    /**
     * Default incremental read: same schema as full.
     * 默认增量读取：与全量同步采用同一份 schema。
     */
    override fun readIncrementalSyncData(nbt: NBTTagCompound) {
        readFullSyncData(nbt)
    }
}
