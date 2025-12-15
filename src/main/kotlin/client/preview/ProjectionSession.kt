package github.kasuminova.prototypemachinery.client.preview

import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import net.minecraft.client.Minecraft
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

internal enum class ProjectionRenderMode {
    /** Render all required blocks */
    ALL,
    /** Render only blocks that are not matching (mismatch/unloaded/unknown) */
    MISMATCH_ONLY
}

internal enum class ProjectionVisualMode {
    /** Only outlines (wireframe) */
    OUTLINE,
    /** Filled translucent cubes (ghost blocks) */
    GHOST,
    /** Both ghost + outline */
    BOTH,
    /** Render textured block models (slower than GHOST). */
    BLOCK_MODEL
}

/**
 * Mutable session state for world projection.
 *
 * 世界投影的会话状态（客户端）。
 */
internal data class StructureProjectionSession(
    val structureId: String,
    var anchor: BlockPos,
    val sliceCountOverride: Int? = null,
    /** Follow player horizontal facing (auto updates currentOrientation). */
    var followPlayerFacing: Boolean = true,
    /** Legacy fallback for 4-way lock; if set, orientation defaults to (frontOverride, UP). */
    var frontOverride: EnumFacing? = null,
    /** Locked orientation (front+top). When followPlayerFacing=false, this will be used. */
    var lockedOrientation: StructureOrientation? = null,
    val anchorProvider: ((Minecraft) -> BlockPos?)? = null,
    val renderMode: ProjectionRenderMode = ProjectionRenderMode.MISMATCH_ONLY,
    /** Render distance (blocks). Null to use default. / 渲染距离（格），null 表示使用默认值。 */
    val maxRenderDistance: Double? = null,
    val visualMode: ProjectionVisualMode = ProjectionVisualMode.GHOST,
) {
    // resolved per tick
    var currentOrientation: StructureOrientation? = null

    // cached model & expanded entries
    var cachedModel: StructurePreviewModel? = null
    var modelDirty: Boolean = true

    var entries: List<WorldProjectionManager.Entry> = emptyList()
    var statuses: Array<WorldProjectionManager.Status> = emptyArray()
    var statusCursor: Int = 0
    var renderCursor: Int = 0
    var chunkRenderCursor: Int = 0

    val defaultSliceCount: Int
        get() = sliceCountOverride ?: 1

    fun invalidateModel() {
        modelDirty = true
    }

    fun invalidateEntries() {
        entries = emptyList()
        statuses = emptyArray()
        statusCursor = 0
        renderCursor = 0
        chunkRenderCursor = 0
    }

    fun invalidateStatuses() {
        statuses = Array(entries.size) { WorldProjectionManager.Status.UNKNOWN }
        statusCursor = 0
    }

    fun ensureEntries(model: StructurePreviewModel) {
        if (entries.isNotEmpty()) return
        entries = model.blocks.entries.map { (pos, req) ->
            WorldProjectionManager.Entry(pos, req)
        }
        statuses = Array(entries.size) { WorldProjectionManager.Status.UNKNOWN }
        statusCursor = 0
        renderCursor = 0
        chunkRenderCursor = 0
    }
}
