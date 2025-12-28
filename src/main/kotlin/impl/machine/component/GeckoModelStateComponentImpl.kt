package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.base.SynchronizableComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.GeckoModelStateComponent
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraft.nbt.NBTTagString

/**
 * Default implementation of [GeckoModelStateComponent].
 *
 * Synchronization granularity is at the component level.
 * 同步粒度在组件级别。
 */
public class GeckoModelStateComponentImpl(
    override val owner: MachineInstance,
    override val type: MachineComponentType<*>
) : GeckoModelStateComponent, SynchronizableComponent {

    override val provider: Any? = null

    private val layersMutable: MutableList<String> = mutableListOf()

    override val animationLayers: List<String>
        get() = layersMutable

    override val animationName: String?
        get() = layersMutable.firstOrNull()

    private var dirty: Boolean = false

    override var stateVersion: Int = 0
        private set

    override fun setAnimationLayers(layers: List<String>) {
        val cleaned = layers.map { it.trim() }.filter { it.isNotEmpty() }
        if (layersMutable == cleaned) return

        layersMutable.clear()
        layersMutable.addAll(cleaned)

        bumpAndSync()
    }

    override fun setAnimation(name: String?) {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) {
            if (layersMutable.isEmpty()) return
            layersMutable.clear()
            bumpAndSync()
            return
        }

        if (layersMutable.size == 1 && layersMutable[0] == n) return
        layersMutable.clear()
        layersMutable.add(n)
        bumpAndSync()
    }

    override fun clear() {
        if (layersMutable.isEmpty()) return
        layersMutable.clear()
        bumpAndSync()
    }

    private fun bumpAndSync() {
        stateVersion++
        dirty = true
        sync()
    }

    // ========== Serializable ==========

    override fun writeNBT(): NBTTagCompound {
        val tag = NBTTagCompound()
        tag.setInteger(KEY_STATE_VERSION, stateVersion)
        tag.setTag(KEY_LAYERS, writeLayersNBT())
        return tag
    }

    override fun readNBT(nbt: NBTTagCompound) {
        if (nbt.hasKey(KEY_STATE_VERSION)) {
            stateVersion = nbt.getInteger(KEY_STATE_VERSION)
        }
        if (nbt.hasKey(KEY_LAYERS)) {
            readLayersNBT(nbt.getTagList(KEY_LAYERS, 8))
        }
    }

    // ========== SynchronizableComponent ==========

    override fun writeFullSyncData(nbt: NBTTagCompound) {
        // Always include version + full layers
        nbt.setInteger(KEY_STATE_VERSION, stateVersion)
        nbt.setTag(KEY_LAYERS, writeLayersNBT())
    }

    override fun writeIncrementalSyncData(nbt: NBTTagCompound): Boolean {
        if (!dirty) return false
        dirty = false

        nbt.setInteger(KEY_STATE_VERSION, stateVersion)
        nbt.setTag(KEY_LAYERS, writeLayersNBT())
        return true
    }

    override fun readFullSyncData(nbt: NBTTagCompound) {
        readNBT(nbt)
    }

    override fun readIncrementalSyncData(nbt: NBTTagCompound) {
        // Same schema as full sync; keys are optional.
        if (nbt.hasKey(KEY_STATE_VERSION)) {
            stateVersion = nbt.getInteger(KEY_STATE_VERSION)
        }
        if (nbt.hasKey(KEY_LAYERS)) {
            readLayersNBT(nbt.getTagList(KEY_LAYERS, 8))
        }
    }

    private fun writeLayersNBT(): NBTTagList {
        val list = NBTTagList()
        for (s in layersMutable) {
            list.appendTag(NBTTagString(s))
        }
        return list
    }

    private fun readLayersNBT(list: NBTTagList) {
        layersMutable.clear()
        for (i in 0 until list.tagCount()) {
            val s = list.getStringTagAt(i).trim()
            if (s.isNotEmpty()) layersMutable.add(s)
        }
    }

    private companion object {
        private const val KEY_LAYERS = "Layers"
        private const val KEY_STATE_VERSION = "StateVersion"
    }
}
