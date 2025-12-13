package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.base.SynchronizableComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponent
import github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.data.ZenMachineData
import net.minecraft.nbt.NBTTagCompound

/**
 * Implementation of ZSDataComponent.
 * ZSDataComponent 的实现。
 *
 * Synchronization granularity is at the component level (full sync only).
 * 同步粒度在组件级别（仅全量同步）。
 */
public class ZSDataComponentImpl(
    override val owner: MachineInstance,
    override val type: MachineComponentType<*>
) : ZSDataComponent, SynchronizableComponent {

    override val provider: Any? = null

    override val data: ZenMachineData = ZenMachineData {
        // Mark for sync on any change
        // 任何变更时标记为待同步
        sync()
    }

    // ========== Serializable ==========

    override fun writeNBT(): NBTTagCompound {
        val tag = NBTTagCompound()
        tag.setTag("Data", data.writeNBT())
        return tag
    }

    override fun readNBT(nbt: NBTTagCompound) {
        if (nbt.hasKey("Data")) {
            data.readNBT(nbt.getCompoundTag("Data"))
        }
    }

    // ========== SynchronizableComponent ==========

    override fun writeFullSyncData(nbt: NBTTagCompound) {
        nbt.setTag("Data", data.writeNBT())
    }

    override fun writeIncrementalSyncData(nbt: NBTTagCompound): Boolean {
        // Simplified: always do full sync for this component
        // 简化：此组件总是做全量同步
        nbt.setTag("Data", data.writeNBT())
        return true
    }

    override fun readFullSyncData(nbt: NBTTagCompound) {
        if (nbt.hasKey("Data")) {
            data.readNBT(nbt.getCompoundTag("Data"))
        }
    }

    override fun readIncrementalSyncData(nbt: NBTTagCompound) {
        // Same as full sync
        // 与全量同步相同
        readFullSyncData(nbt)
    }
}
