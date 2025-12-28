package github.kasuminova.prototypemachinery.common.structure.serialization

import kotlinx.serialization.Serializable

/**
 * Serializable data class for BlockPos.
 * BlockPos 的可序列化数据类。
 */
@Serializable
public data class BlockPosData(
    val x: Int,
    val y: Int,
    val z: Int
)

/**
 * Serializable data class for structure pattern element.
 * 结构模式元素的可序列化数据类。
 */
@Serializable
public data class StructurePatternElementData(
    val pos: BlockPosData,
    val blockId: String,           // e.g., "minecraft:stone"
    val meta: Int = 0,
    val nbt: Map<String, String>? = null,  // Simplified NBT as string map
    /** Optional alternatives for this position. When present, build/preview may choose any one. */
    val alternatives: List<StructurePatternAlternativeData> = emptyList()
)

/** A single alternative option for [StructurePatternElementData]. */
@Serializable
public data class StructurePatternAlternativeData(
    val blockId: String,
    val meta: Int = 0,
    val nbt: Map<String, String>? = null
)

/**
 * Serializable data class for structure definition.
 * 结构定义的可序列化数据类。
 */
@Serializable
public data class StructureData(
    val id: String,
    /** Optional human-friendly name for UI display. If omitted, UIs should fall back to [id]. */
    val name: String? = null,
    val type: String,              // e.g., "template", "slice"
    val offset: BlockPosData = BlockPosData(0, 0, 0),
    val hideWorldBlocks: Boolean = false,
    val pattern: List<StructurePatternElementData>,
    val validators: List<String> = emptyList(),  // Validator type IDs
    val children: List<String> = emptyList(),    // Child structure IDs (references)
    // Slice-specific fields
    val minCount: Int? = null,     // Minimum slice count (for slice type)
    val maxCount: Int? = null,     // Maximum slice count (for slice type)
    val sliceOffset: BlockPosData? = null  // Offset applied per slice iteration (for slice type)
)
