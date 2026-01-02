package github.kasuminova.prototypemachinery.common.structure.validator

import github.kasuminova.prototypemachinery.api.machine.structure.logic.JsonConfigurableStructureValidator
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos

/**
 * A best-effort TileEntity root-NBT validator.
 *
 * Designed mainly for migration of MM/MMCE `nbt` constraints, especially for positions that also have
 * multiple block alternatives (where predicate-side NBT matching is currently limited).
 *
 * JSON params schema (in `StructureData.validators`):
 *
 * ```json
 * {
 *   "id": "prototypemachinery:tile_nbt",
 *   "checks": [
 *     {
 *       "pos": {"x": 1, "y": 0, "z": 0},
 *       "nbt": {"SomeKey": "SomeValue"}
 *     }
 *   ]
 * }
 * ```
 *
 * Notes / 限制：
 * - Matching is shallow (no nested paths). Keys are looked up on the TE root tag.
 * - Value comparison is best-effort (string tag => getString; otherwise => NBTBase.toString()).
 */
public class TileEntityNbtStructureValidator : JsonConfigurableStructureValidator {

    private data class Check(
        val pos: BlockPos,
        val nbt: Map<String, String>,
        val blockId: ResourceLocation? = null,
        val meta: Int? = null,
    )

    private var checks: List<Check> = emptyList()

    override fun configure(params: JsonObject) {
        val checksEl = params["checks"] ?: return
        val arr: JsonArray = checksEl.jsonArray

        val parsed = ArrayList<Check>(arr.size)
        for (el in arr) {
            if (el !is JsonObject) continue

            val posObj = el["pos"]?.jsonObject
                ?: throw IllegalArgumentException("tile_nbt.checks[].pos is required")

            val x = posObj["x"].asIntOrNull()
                ?: throw IllegalArgumentException("tile_nbt.checks[].pos.x is required")
            val y = posObj["y"].asIntOrNull()
                ?: throw IllegalArgumentException("tile_nbt.checks[].pos.y is required")
            val z = posObj["z"].asIntOrNull()
                ?: throw IllegalArgumentException("tile_nbt.checks[].pos.z is required")

            val nbtObj = el["nbt"]?.jsonObject
                ?: throw IllegalArgumentException("tile_nbt.checks[].nbt is required")
            val nbt = jsonObjectToShallowStringMap(nbtObj)
            if (nbt.isEmpty()) continue

            val blockId = el["blockId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?.let { ResourceLocation(it) }
            val meta = el["meta"].asIntOrNull()

            parsed.add(Check(pos = BlockPos(x, y, z), nbt = nbt, blockId = blockId, meta = meta))
        }

        checks = parsed
    }

    override fun validate(context: StructureMatchContext, offset: BlockPos): Boolean {
        if (checks.isEmpty()) return true

        val world = context.machine.blockEntity.world
        for (check in checks) {
            val worldPos = offset.add(check.pos)

            if (check.blockId != null) {
                val state = world.getBlockState(worldPos)
                val actualId = state.block.registryName
                if (actualId == null || actualId != check.blockId) return false

                if (check.meta != null) {
                    @Suppress("DEPRECATION")
                    val actualMeta = state.block.getMetaFromState(state)
                    if (actualMeta != check.meta) return false
                }
            }

            val te: TileEntity = world.getTileEntity(worldPos) ?: return false
            val tag = NBTTagCompound()
            te.writeToNBT(tag)

            for ((k, expected) in check.nbt) {
                if (!tag.hasKey(k)) return false

                val base: NBTBase = tag.getTag(k) ?: return false
                val actual = when (base) {
                    is NBTTagString -> tag.getString(k)
                    else -> base.toString()
                }

                if (actual != expected) return false
            }
        }

        return true
    }

    private fun jsonObjectToShallowStringMap(obj: JsonObject): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in obj) {
            out[k] = jsonElementToCompactString(v)
        }
        return out
    }

    private fun jsonElementToCompactString(el: JsonElement): String {
        return when (el) {
            is JsonPrimitive -> el.content
            else -> el.toString()
        }
    }

    private fun JsonElement?.asIntOrNull(): Int? {
        val prim = this as? JsonPrimitive ?: return null
        return prim.content.toIntOrNull()
    }
}
