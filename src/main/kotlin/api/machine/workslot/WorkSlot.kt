package github.kasuminova.prototypemachinery.api.machine.workslot

import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import net.minecraft.util.ResourceLocation

/**
 * Runtime work slot state owned by a machine instance.
 *
 * 机器实例运行时持有的“工位”状态。
 *
 * NOTE: This is intentionally minimal for now.
 * - Status is derived from current process in the first iteration.
 * - Future iterations may add persisted data/overlays and richer status.
 */
public interface WorkSlot {

    /** Stable identifier. / 稳定标识 */
    public val name: String

    /** Allowed recipe groups for scanning. / 允许的配方组 */
    public val allowedRecipeGroups: Set<ResourceLocation>

    /** Currently running process (0 or 1). / 当前运行的进程（0 或 1 个） */
    public var process: RecipeProcess?

    /** Optional UI/status line. / 可选的状态文本 */
    public var statusText: String?

    /** Derived minimal status. / 派生的最小状态 */
    public val status: WorkSlotStatus
        get() = if (process == null) WorkSlotStatus.IDLE else WorkSlotStatus.RUNNING
}
