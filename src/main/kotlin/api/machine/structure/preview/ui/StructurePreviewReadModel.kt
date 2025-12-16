package github.kasuminova.prototypemachinery.api.machine.structure.preview.ui

import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.PreviewBomEntry
import net.minecraft.util.math.BlockPos

/**
 * Read-only data protocol for structure preview UIs.
 *
 * 结构预览 UI 的只读数据协议（跨宿主复用：控制器 GUI / 独立预览 / JEI 等）。
 *
 * Design goals / 设计目标：
 * - Core/UI 只依赖这些纯数据类型，不直接依赖“世界扫描/渲染/输入系统”。
 * - 同一套 UI 组件可在不同宿主复用；宿主可选择是否填充 [worldPos]、[orientation] 等。
 */
public data class StructurePreviewReadModel(
    /** The structure id this preview is for. */
    public val structureId: String,
    /** Placement orientation. Null means host does not provide orientation (e.g. JEI). */
    public val orientation: StructureOrientation? = null,
    /** Anchor position in world coordinates. Null means no world placement (e.g. JEI). */
    public val anchor: BlockPos? = null,
    /** Flattened per-block entries. */
    public val entries: List<StructurePreviewEntry>,
    /** Legacy/simple BOM (requirement + total count). */
    public val bom: List<PreviewBomEntry> = emptyList(),
    /** BOM enriched with per-status counters, suitable for UI filtering/sorting. */
    public val bomLines: List<StructurePreviewBomLine> = emptyList(),
    /** Aggregated counters for the whole preview. */
    public val stats: StructurePreviewStats = StructurePreviewStats.EMPTY
)

/**
 * A single required block entry in structure preview.
 */
public data class StructurePreviewEntry(
    /** Relative position (controller origin = (0,0,0)) in preview model coordinates. */
    public val relativePos: BlockPos,
    /** Optional absolute world position (filled by host/scanner). */
    public val worldPos: BlockPos? = null,
    /** Requirement descriptor for rendering/BOM/tooltip. */
    public val requirement: BlockRequirement,
    /** Current match status (filled by host/scanner). */
    public val status: StructurePreviewEntryStatus = StructurePreviewEntryStatus.UNKNOWN,
    /**
     * Instance path of the entry (optional).
     *
     * Used to support “可操作子结构”：当结构存在 children/slices 时，UI 可用 path
     * 将条目归属到某个子结构实例，并允许对该实例设置局部预览参数（如 sliceCount）。
     */
    public val instancePath: PreviewInstancePath? = null
) {
    /** Stable unique-ish identifier for selection/cache. */
    public fun stableId(): String {
        val path = instancePath?.stableKey()?.let { "@$it" } ?: ""
        return "${relativePos.x},${relativePos.y},${relativePos.z}:${requirement.stableKey()}$path"
    }
}

/**
 * Preview entry match status.
 */
public enum class StructurePreviewEntryStatus {
    /** World matches the requirement. */
    MATCH,
    /** Required block is missing (commonly air). */
    MISSING,
    /** Block exists but does not satisfy requirement. */
    WRONG,
    /** Chunk/area not loaded, cannot decide. */
    UNLOADED,
    /** Not evaluated yet / host does not provide world scan. */
    UNKNOWN
}

/**
 * Aggregated counters for a whole preview.
 */
public data class StructurePreviewStats(
    public val total: Int,
    public val match: Int,
    public val missing: Int,
    public val wrong: Int,
    public val unloaded: Int,
    public val unknown: Int
) {
    public companion object {
        public val EMPTY: StructurePreviewStats = StructurePreviewStats(
            total = 0,
            match = 0,
            missing = 0,
            wrong = 0,
            unloaded = 0,
            unknown = 0
        )

        public fun fromEntries(entries: Iterable<StructurePreviewEntry>): StructurePreviewStats {
            var total = 0
            var match = 0
            var missing = 0
            var wrong = 0
            var unloaded = 0
            var unknown = 0

            for (e in entries) {
                total++
                when (e.status) {
                    StructurePreviewEntryStatus.MATCH -> match++
                    StructurePreviewEntryStatus.MISSING -> missing++
                    StructurePreviewEntryStatus.WRONG -> wrong++
                    StructurePreviewEntryStatus.UNLOADED -> unloaded++
                    StructurePreviewEntryStatus.UNKNOWN -> unknown++
                }
            }

            return StructurePreviewStats(
                total = total,
                match = match,
                missing = missing,
                wrong = wrong,
                unloaded = unloaded,
                unknown = unknown
            )
        }
    }
}

/**
 * Enriched BOM line used by advanced preview UIs.
 */
public data class StructurePreviewBomLine(
    public val requirement: BlockRequirement,
    public val requiredCount: Int,
    public val match: Int = 0,
    public val missing: Int = 0,
    public val wrong: Int = 0,
    public val unloaded: Int = 0,
    public val unknown: Int = 0
) {
    /** Stable key for grouping/sorting. */
    public val key: String get() = requirement.stableKey()

    public val mismatchCount: Int get() = missing + wrong
}

/**
 * A stable path identifying a specific structure instance inside an expanded preview.
 *
 * Note: This is intentionally data-only. How the path is produced is up to preview builder/expander.
 */
public data class PreviewInstancePath(
    public val segments: List<Segment>
) {
    public sealed interface Segment {
        /** A stable string id used for selection/caching. */
        public fun stableKey(): String
    }

    /** A plain child structure instance segment. */
    public data class Child(public val structureId: String) : Segment {
        override fun stableKey(): String = "child:$structureId"
    }

    /** A slice instance segment (for repeated slice-like structures). */
    public data class Slice(public val structureId: String, public val index: Int) : Segment {
        override fun stableKey(): String = "slice:$structureId#$index"
    }

    public fun stableKey(): String = segments.joinToString("/") { it.stableKey() }
}
