package github.kasuminova.prototypemachinery.common.buildinstrument

import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.BlockPos

internal object BuildInstrumentNbt {

    private const val KEY_BOUND = "Bound"
    private const val KEY_BOUND_DIM = "BoundDim"
    private const val KEY_BOUND_X = "BoundX"
    private const val KEY_BOUND_Y = "BoundY"
    private const val KEY_BOUND_Z = "BoundZ"
    private const val KEY_BOUND_MACHINE_ID = "BoundMachineId"
    internal const val KEY_BOUND_STRUCTURE_ID = "BoundStructureId"

    private const val KEY_TASK = "Task"
    private const val KEY_TASK_STATE = "State"
    private const val KEY_TASK_TOTAL = "Total"
    private const val KEY_TASK_DONE = "Done"
    private const val KEY_TASK_LAST_SYNC_TICK = "LastSyncTick"

    // Material selection overrides (for AnyOfRequirement)
    private const val KEY_MATERIAL_SELECTIONS = "MaterialSelections"

    enum class TaskState(val id: Int) {
        IDLE(0),
        BUILDING(1),
        PAUSED_BUILDING(2),
        DISASSEMBLING(3),
        PAUSED_DISASSEMBLING(4);

        companion object {
            fun fromId(id: Int): TaskState = values().firstOrNull { it.id == id } ?: IDLE
        }
    }

    fun writeBoundController(tag: NBTTagCompound, dim: Int, pos: BlockPos, machineId: String, structureId: String) {
        tag.setBoolean(KEY_BOUND, true)
        tag.setInteger(KEY_BOUND_DIM, dim)
        tag.setInteger(KEY_BOUND_X, pos.x)
        tag.setInteger(KEY_BOUND_Y, pos.y)
        tag.setInteger(KEY_BOUND_Z, pos.z)
        tag.setString(KEY_BOUND_MACHINE_ID, machineId)
        tag.setString(KEY_BOUND_STRUCTURE_ID, structureId)
    }

    fun isBound(tag: NBTTagCompound?): Boolean = tag?.getBoolean(KEY_BOUND) == true

    fun readBoundDim(tag: NBTTagCompound): Int = tag.getInteger(KEY_BOUND_DIM)

    fun readBoundPos(tag: NBTTagCompound): BlockPos = BlockPos(tag.getInteger(KEY_BOUND_X), tag.getInteger(KEY_BOUND_Y), tag.getInteger(KEY_BOUND_Z))

    fun readBoundMachineId(tag: NBTTagCompound): String = tag.getString(KEY_BOUND_MACHINE_ID)

    fun readBoundStructureId(tag: NBTTagCompound): String = tag.getString(KEY_BOUND_STRUCTURE_ID)

    fun getOrCreateTask(tag: NBTTagCompound): NBTTagCompound {
        if (!tag.hasKey(KEY_TASK)) {
            tag.setTag(KEY_TASK, NBTTagCompound())
        }
        return tag.getCompoundTag(KEY_TASK)
    }

    fun readTaskState(root: NBTTagCompound?): TaskState {
        val t = root?.getCompoundTag(KEY_TASK) ?: return TaskState.IDLE
        return TaskState.fromId(t.getInteger(KEY_TASK_STATE))
    }

    fun writeTaskState(root: NBTTagCompound, state: TaskState) {
        val t = getOrCreateTask(root)
        t.setInteger(KEY_TASK_STATE, state.id)
    }

    fun readTaskTotal(root: NBTTagCompound?): Int {
        val t = root?.getCompoundTag(KEY_TASK) ?: return 0
        return t.getInteger(KEY_TASK_TOTAL)
    }

    fun readTaskDone(root: NBTTagCompound?): Int {
        val t = root?.getCompoundTag(KEY_TASK) ?: return 0
        return t.getInteger(KEY_TASK_DONE)
    }

    fun writeTaskProgress(root: NBTTagCompound, done: Int, total: Int) {
        val t = getOrCreateTask(root)
        t.setInteger(KEY_TASK_DONE, done)
        t.setInteger(KEY_TASK_TOTAL, total)
    }

    fun readLastSyncTick(root: NBTTagCompound?): Long {
        val t = root?.getCompoundTag(KEY_TASK) ?: return 0L
        return t.getLong(KEY_TASK_LAST_SYNC_TICK)
    }

    fun writeLastSyncTick(root: NBTTagCompound, tick: Long) {
        val t = getOrCreateTask(root)
        t.setLong(KEY_TASK_LAST_SYNC_TICK, tick)
    }

    fun writeMaterialSelection(root: NBTTagCompound, requirementKey: String, selectedOptionKey: String) {
        if (!root.hasKey(KEY_MATERIAL_SELECTIONS)) {
            root.setTag(KEY_MATERIAL_SELECTIONS, NBTTagCompound())
        }
        val m = root.getCompoundTag(KEY_MATERIAL_SELECTIONS)
        m.setString(requirementKey, selectedOptionKey)
    }

    fun readMaterialSelection(root: NBTTagCompound?, requirementKey: String): String? {
        val r = root ?: return null
        if (!r.hasKey(KEY_MATERIAL_SELECTIONS)) return null
        val m = r.getCompoundTag(KEY_MATERIAL_SELECTIONS)
        val v = m.getString(requirementKey)
        return if (v.isNullOrEmpty()) null else v
    }
}
