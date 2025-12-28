package github.kasuminova.prototypemachinery.api.machine.structure.preview

import net.minecraft.util.math.BlockPos

/**
 * A pure-data model for structure preview UI.
 *
 * 结构预览 UI 的纯数据模型。
 */
public data class StructurePreviewModel(
    /** Required blocks keyed by relative position (controller origin = (0,0,0)) */
    public val blocks: Map<BlockPos, BlockRequirement>,
    /** Inclusive bounds of [blocks] */
    public val bounds: PreviewBounds,
    /** Aggregated bill-of-materials */
    public val bom: List<PreviewBomEntry>
)

public data class PreviewBounds(
    public val min: BlockPos,
    public val max: BlockPos
)

/**
 * A stable requirement descriptor used for preview rendering and BOM aggregation.
 */
public sealed interface BlockRequirement {

    /** Stable key used for aggregation/caching. Must not depend on identity. */
    public fun stableKey(): String

}

/**
 * A requirement that can be satisfied by any one of the provided exact block-state options.
 *
 * 用于表达“多种方块任选其一”的需求（例如 钻石块/金块 选其一）。
 */
public data class AnyOfRequirement(
    public val options: List<ExactBlockStateRequirement>
) : BlockRequirement {
    init {
        require(options.isNotEmpty()) { "AnyOfRequirement.options must not be empty" }
    }

    override fun stableKey(): String {
        // Order-independent stable key.
        val keys = options.asSequence().map { it.stableKey() }.sorted().joinToString("|")
        return "any_of:$keys"
    }
}

/** Exact block-state requirement (block id + meta + properties). */
public data class ExactBlockStateRequirement(
    public val blockId: net.minecraft.util.ResourceLocation,
    public val meta: Int,
    public val properties: Map<String, String> = emptyMap()
) : BlockRequirement {
    override fun stableKey(): String {
        if (properties.isEmpty()) return "state:${blockId}:$meta"
        val props = properties.entries
            .sortedBy { it.key }
            .joinToString(",") { (k, v) -> "$k=$v" }
        return "state:${blockId}:$meta{$props}"
    }
}

/**
 * Exact block-state requirement plus shallow TileEntity NBT constraints.
 *
 * This is used by predicates that need to match TE state in addition to block state.
 */
public data class ExactBlockStateWithNbtRequirement(
    public val blockId: net.minecraft.util.ResourceLocation,
    public val meta: Int,
    public val properties: Map<String, String> = emptyMap(),
    /** Shallow constraints: key -> expected string value (best-effort SNBT / string) */
    public val nbtConstraints: Map<String, String> = emptyMap()
) : BlockRequirement {
    override fun stableKey(): String {
        val propsPart = if (properties.isEmpty()) "" else {
            properties.entries
                .sortedBy { it.key }
                .joinToString(",") { (k, v) -> "$k=$v" }
                .let { "{$it}" }
        }

        val nbtPart = if (nbtConstraints.isEmpty()) "" else {
            nbtConstraints.entries
                .sortedBy { it.key }
                .joinToString(",") { (k, v) -> "$k=$v" }
                .let { "[nbt:$it]" }
        }

        return "state_nbt:${blockId}:$meta$propsPart$nbtPart"
    }
}

/**
 * A lightweight requirement for predicates that can be expressed but are not tied to a block state.
 * Intended mostly for tests / future tags.
 */
public data class LiteralRequirement(public val key: String) : BlockRequirement {
    override fun stableKey(): String = "literal:$key"
}

/** Fallback when a predicate cannot be represented yet. */
public data class UnknownRequirement(public val debug: String) : BlockRequirement {
    override fun stableKey(): String = "unknown:$debug"
}

public data class PreviewBomEntry(
    public val requirement: BlockRequirement,
    public val count: Int
)
