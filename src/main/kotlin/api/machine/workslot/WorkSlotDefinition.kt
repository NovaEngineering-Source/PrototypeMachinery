package github.kasuminova.prototypemachinery.api.machine.workslot

import net.minecraft.util.ResourceLocation

/**
 * Static work slot definition on a machine type.
 *
 * 机器类型（MachineType）上的静态工位定义。
 */
public data class WorkSlotDefinition(
    /** Stable identifier shown to scripts/UI/logs. / 稳定标识（脚本/UI/日志使用） */
    val name: String,

    /**
     * Allowed recipe groups for this slot.
     *
     * 该工位允许的配方组白名单。
     */
    val allowedRecipeGroups: Set<ResourceLocation> = emptySet(),

    /**
     * Whether this slot always exists.
     *
     * 是否始终存在（默认 true）。
     */
    val isCore: Boolean = true,

    /** Restart policy when blocked. / 卡住时的策略 */
    val restartPolicy: WorkSlotRestartPolicy = WorkSlotRestartPolicy.LOCKED_RETRY,
)
