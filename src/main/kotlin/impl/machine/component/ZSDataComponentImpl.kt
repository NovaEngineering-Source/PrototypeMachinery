package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.base.DirtySynchronizableComponent
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
) : ZSDataComponent, DirtySynchronizableComponent() {

    override val provider: Any? = null

    override val data: ZenMachineData = ZenMachineData {
        // Mark for sync on any change
        // 任何变更时标记为待同步
        markDirty()
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
    // Uses DirtySynchronizableComponent defaults:
    // - incremental sync == full sync when dirty
    // - incremental read == full read
}
