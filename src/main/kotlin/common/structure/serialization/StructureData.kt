package github.kasuminova.prototypemachinery.common.structure.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

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
 * Structure validator entry.
 *
 * Backward compatible JSON forms:
 * - string: "modid:validator_id"
 * - object: {"id":"modid:validator_id", ...params }
 * - object: {"type":"modid:validator_id", ...params } (alias)
 *
 * All extra keys (except id/type) are treated as params.
 */
@Serializable(with = StructureValidatorSpecDataSerializer::class)
public data class StructureValidatorSpecData(
    val id: String,
    /** Arbitrary json params passed to configurable validators. */
    val params: JsonObject = buildJsonObject { }
)

public object StructureValidatorSpecDataSerializer : KSerializer<StructureValidatorSpecData> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(
        "github.kasuminova.prototypemachinery.common.structure.serialization.StructureValidatorSpecData",
        PrimitiveKind.STRING
    )

    override fun serialize(encoder: Encoder, value: StructureValidatorSpecData) {
        val jsonEncoder = encoder as? JsonEncoder
            ?: throw SerializationException("StructureValidatorSpecDataSerializer only supports JSON")

        if (value.params.isEmpty()) {
            jsonEncoder.encodeJsonElement(JsonPrimitive(value.id))
            return
        }

        val obj = buildJsonObject {
            put("id", JsonPrimitive(value.id))
            for ((k, v) in value.params) {
                put(k, v)
            }
        }
        jsonEncoder.encodeJsonElement(obj)
    }

    override fun deserialize(decoder: Decoder): StructureValidatorSpecData {
        val jsonDecoder = decoder as? JsonDecoder
            ?: throw SerializationException("StructureValidatorSpecDataSerializer only supports JSON")

        val el: JsonElement = jsonDecoder.decodeJsonElement()
        return when (el) {
            is JsonPrimitive -> {
                if (!el.isString) {
                    throw SerializationException("Validator entry must be a string id or an object: got $el")
                }
                StructureValidatorSpecData(id = el.content)
            }

            is JsonObject -> {
                val idEl = el["id"] ?: el["type"]
                    ?: throw SerializationException("Validator object must contain 'id' (or 'type') field: $el")
                val id = idEl.jsonPrimitive.content

                val params = buildJsonObject {
                    for ((k, v) in el) {
                        if (k == "id" || k == "type") continue
                        put(k, v)
                    }
                }
                StructureValidatorSpecData(id = id, params = params)
            }

            else -> throw SerializationException("Validator entry must be a string id or an object: got $el")
        }
    }
}

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
    val validators: List<StructureValidatorSpecData> = emptyList(),  // Validator IDs + params
    val children: List<String> = emptyList(),    // Child structure IDs (references)
    // Slice-specific fields
    val minCount: Int? = null,     // Minimum slice count (for slice type)
    val maxCount: Int? = null,     // Maximum slice count (for slice type)
    val sliceOffset: BlockPosData? = null  // Offset applied per slice iteration (for slice type)
)
