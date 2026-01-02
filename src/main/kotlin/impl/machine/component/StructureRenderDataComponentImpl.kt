package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.base.DirtySynchronizableComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.StructureRenderDataComponent
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos

internal class StructureRenderDataComponentImpl(
    override val owner: MachineInstance,
    override val type: MachineComponentType<*>,
) : StructureRenderDataComponent, DirtySynchronizableComponent() {

    override val provider: Any? = null

    override var dataVersion: Int = 0
        private set

    override var structureMin: BlockPos? = null
        private set

    override var structureMax: BlockPos? = null
        private set

    private val _sliceCounts: MutableMap<String, Int> = HashMap()
    override val sliceCounts: Map<String, Int>
        get() = _sliceCounts

    override fun updateFromServer(
        formed: Boolean,
        min: BlockPos?,
        max: BlockPos?,
        sliceCounts: Map<String, Int>,
    ): Boolean {
        var changed = false

        if (!formed) {
            if (structureMin != null || structureMax != null || _sliceCounts.isNotEmpty()) {
                structureMin = null
                structureMax = null
                _sliceCounts.clear()
                changed = true
            }

            if (changed) {
                bumpVersion()
                // MachineInstanceImpl already triggers syncComponent(c); avoid double-sync.
                markDirty(syncNow = false)
            }
            return changed
        }

        // Bounds
        if (min != structureMin || max != structureMax) {
            structureMin = min
            structureMax = max
            changed = true
        }

        // Slice counts (simplified): treat as snapshot.
        if (_sliceCounts != sliceCounts) {
            _sliceCounts.clear()
            _sliceCounts.putAll(sliceCounts)
            changed = true
        }

        if (changed) {
            bumpVersion()
            // MachineInstanceImpl already triggers syncComponent(c); avoid double-sync.
            markDirty(syncNow = false)
        }

        return changed
    }

    override fun writeFullSyncData(nbt: NBTTagCompound) {
        // Full snapshot; absence means empty/cleared.
        nbt.setInteger("V", dataVersion)

        val min = structureMin
        val max = structureMax
        if (min != null && max != null) {
            nbt.setIntArray("Min", intArrayOf(min.x, min.y, min.z))
            nbt.setIntArray("Max", intArrayOf(max.x, max.y, max.z))
        }

        if (_sliceCounts.isNotEmpty()) {
            val sc = NBTTagCompound()
            for ((k, v) in _sliceCounts) {
                sc.setInteger(k, v)
            }
            nbt.setTag("S", sc)
        }
    }

    override fun readFullSyncData(nbt: NBTTagCompound) {
        // Full snapshot: reset then apply.
        _sliceCounts.clear()
        structureMin = null
        structureMax = null

        if (nbt.hasKey("V")) {
            dataVersion = nbt.getInteger("V")
        }

        if (nbt.hasKey("Min") && nbt.hasKey("Max")) {
            val a = nbt.getIntArray("Min")
            val b = nbt.getIntArray("Max")
            if (a.size >= 3 && b.size >= 3) {
                structureMin = BlockPos(a[0], a[1], a[2])
                structureMax = BlockPos(b[0], b[1], b[2])
            }
        }

        if (nbt.hasKey("S")) {
            val sc = nbt.getCompoundTag("S")
            for (k in sc.keySet) {
                _sliceCounts[k] = sc.getInteger(k)
            }
        }
    }

    private fun bumpVersion() {
        dataVersion = (dataVersion + 1) and Int.MAX_VALUE
    }
}
