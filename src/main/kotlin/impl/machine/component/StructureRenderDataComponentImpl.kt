package github.kasuminova.prototypemachinery.impl.machine.component

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.base.SynchronizableComponent
import github.kasuminova.prototypemachinery.api.machine.component.type.StructureRenderDataComponent
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos

internal class StructureRenderDataComponentImpl(
    override val owner: MachineInstance,
    override val type: MachineComponentType<*>,
) : StructureRenderDataComponent, SynchronizableComponent {

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

    // Dirty flags + pending diff
    private var dirtyClear: Boolean = false
    private var dirtyBounds: Boolean = false
    private var dirtySlices: Boolean = false

    private val pendingSliceSet: MutableMap<String, Int> = HashMap()
    private val pendingSliceRemove: MutableSet<String> = HashSet()

    override fun updateFromServer(
        formed: Boolean,
        min: BlockPos?,
        max: BlockPos?,
        sliceCounts: Map<String, Int>,
    ): Boolean {
        var changed = false

        if (!formed) {
            if (structureMin != null || structureMax != null || _sliceCounts.isNotEmpty()) {
                changed = true
            }
            if (changed) {
                structureMin = null
                structureMax = null
                _sliceCounts.clear()

                dirtyClear = true
                dirtyBounds = false
                dirtySlices = false
                pendingSliceSet.clear()
                pendingSliceRemove.clear()

                bumpVersion()
            }
            return changed
        }

        // Bounds
        if (min != structureMin || max != structureMax) {
            structureMin = min
            structureMax = max
            dirtyBounds = true
            changed = true
        }

        // Slice counts diff
        if (_sliceCounts.isEmpty() && sliceCounts.isEmpty()) {
            // no-op
        } else {
            // removals
            if (_sliceCounts.isNotEmpty()) {
                for (k in _sliceCounts.keys.toList()) {
                    if (!sliceCounts.containsKey(k)) {
                        _sliceCounts.remove(k)
                        pendingSliceRemove.add(k)
                        pendingSliceSet.remove(k)
                        dirtySlices = true
                        changed = true
                    }
                }
            }
            // updates
            for ((k, v) in sliceCounts) {
                val old = _sliceCounts[k]
                if (old == null || old != v) {
                    _sliceCounts[k] = v
                    pendingSliceSet[k] = v
                    pendingSliceRemove.remove(k)
                    dirtySlices = true
                    changed = true
                }
            }
        }

        if (changed) {
            dirtyClear = false
            bumpVersion()
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

    override fun writeIncrementalSyncData(nbt: NBTTagCompound): Boolean {
        var wrote = false

        if (dirtyClear) {
            nbt.setBoolean("Clr", true)
            nbt.setInteger("V", dataVersion)
            // Clearing supersedes other diffs.
            dirtyClear = false
            dirtyBounds = false
            dirtySlices = false
            pendingSliceSet.clear()
            pendingSliceRemove.clear()
            return true
        }

        if (dirtyBounds) {
            val min = structureMin
            val max = structureMax
            if (min != null && max != null) {
                nbt.setIntArray("Min", intArrayOf(min.x, min.y, min.z))
                nbt.setIntArray("Max", intArrayOf(max.x, max.y, max.z))
                wrote = true
            } else {
                // treat missing bounds as clear (should be rare when formed=true)
                nbt.setBoolean("Clr", true)
                wrote = true
            }
            dirtyBounds = false
        }

        if (dirtySlices) {
            if (pendingSliceSet.isNotEmpty()) {
                val set = NBTTagCompound()
                for ((k, v) in pendingSliceSet) {
                    set.setInteger(k, v)
                }
                nbt.setTag("Set", set)
                pendingSliceSet.clear()
                wrote = true
            }

            if (pendingSliceRemove.isNotEmpty()) {
                nbt.setString("Rem", pendingSliceRemove.joinToString("\u0000"))
                pendingSliceRemove.clear()
                wrote = true
            }

            dirtySlices = false
        }

        if (wrote) {
            nbt.setInteger("V", dataVersion)
        }

        return wrote
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

    override fun readIncrementalSyncData(nbt: NBTTagCompound) {
        if (nbt.hasKey("V")) {
            dataVersion = nbt.getInteger("V")
        }

        if (nbt.getBoolean("Clr")) {
            structureMin = null
            structureMax = null
            _sliceCounts.clear()
            return
        }

        if (nbt.hasKey("Min") && nbt.hasKey("Max")) {
            val a = nbt.getIntArray("Min")
            val b = nbt.getIntArray("Max")
            if (a.size >= 3 && b.size >= 3) {
                structureMin = BlockPos(a[0], a[1], a[2])
                structureMax = BlockPos(b[0], b[1], b[2])
            }
        }

        if (nbt.hasKey("Set")) {
            val set = nbt.getCompoundTag("Set")
            for (k in set.keySet) {
                _sliceCounts[k] = set.getInteger(k)
            }
        }

        if (nbt.hasKey("Rem")) {
            val remRaw = nbt.getString("Rem")
            if (remRaw.isNotEmpty()) {
                val keys = remRaw.split('\u0000')
                for (k in keys) {
                    if (k.isNotEmpty()) _sliceCounts.remove(k)
                }
            }
        }
    }

    private fun bumpVersion() {
        dataVersion = (dataVersion + 1) and Int.MAX_VALUE
    }
}
