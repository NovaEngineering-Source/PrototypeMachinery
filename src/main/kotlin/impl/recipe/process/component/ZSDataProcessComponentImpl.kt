package github.kasuminova.prototypemachinery.impl.recipe.process.component

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponent
import github.kasuminova.prototypemachinery.api.recipe.process.component.RecipeProcessComponentType
import github.kasuminova.prototypemachinery.api.recipe.process.component.type.ZSDataProcessComponent
import github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.data.ZenMachineData
import net.minecraft.nbt.NBTTagCompound

/**
 * Implementation of ZSDataProcessComponent.
 * ZSDataProcessComponent 的实现。
 *
 * Synchronization granularity is at the component level (full sync only).
 * 同步粒度在组件级别（仅全量同步）。
 */
public class ZSDataProcessComponentImpl(
    override val owner: RecipeProcess,
    override val type: RecipeProcessComponentType<*>
) : ZSDataProcessComponent {

    override val data: ZenMachineData = ZenMachineData()

    // ========== Serializable ==========

    override fun serializeNBT(): NBTTagCompound {
        val tag = NBTTagCompound()
        tag.setTag("Data", data.writeNBT())
        return tag
    }

    override fun deserializeNBT(nbt: NBTTagCompound) {
        if (nbt.hasKey("Data")) {
            data.readNBT(nbt.getCompoundTag("Data"))
        }
    }

    // ========== Synchronizable ==========

    override fun writeClientNBT(type: RecipeProcessComponent.Synchronizable.SyncType): NBTTagCompound {
        // Both FULL and INCREMENTAL return full data (simplified)
        // FULL 和 INCREMENTAL 都返回全量数据（简化）
        val tag = NBTTagCompound()
        tag.setTag("Data", data.writeNBT())
        return tag
    }

    override fun readClientNBT(nbt: NBTTagCompound, type: RecipeProcessComponent.Synchronizable.SyncType) {
        // Both types handled the same way
        // 两种类型处理方式相同
        if (nbt.hasKey("Data")) {
            data.readNBT(nbt.getCompoundTag("Data"))
        }
    }
}
