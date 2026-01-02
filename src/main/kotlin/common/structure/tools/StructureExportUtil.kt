package github.kasuminova.prototypemachinery.common.structure.tools

import github.kasuminova.prototypemachinery.common.structure.serialization.BlockPosData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructureData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructurePatternAlternativeData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructurePatternElementData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.init.Blocks
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.util.math.BlockPos
import net.minecraft.world.World
import net.minecraftforge.fml.common.Loader
import java.io.File

internal object StructureExportUtil {

    private val json: Json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
    }

    /**
     * Sanitize to a file/id friendly string.
     *
     * - lowercases
     * - turns whitespace into '_'
     * - drops other non [a-z0-9_\-] chars
     */
    fun sanitizeId(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw.lowercase()) {
            when {
                ch in 'a'..'z' || ch in '0'..'9' -> sb.append(ch)
                ch == '_' || ch == '-' -> sb.append(ch)
                ch.isWhitespace() -> sb.append('_')
                else -> {
                    // drop
                }
            }
        }
        val out = sb.toString().trim('_', '-')
        return if (out.isBlank()) "structure" else out
    }

    fun pmStructuresDir(): File {
        val configDir = Loader.instance().configDir
        return File(configDir, "prototypemachinery/structures")
    }

    fun writeStructureJson(
        data: StructureData,
        subDir: String,
        preferredFileName: String,
    ): File {
        val baseDir = pmStructuresDir()
        val dir = File(baseDir, subDir)
        if (!dir.exists()) dir.mkdirs()

        val safeName = sanitizeId(preferredFileName)
        var file = File(dir, "$safeName.json")
        if (file.exists()) {
            var i = 2
            while (true) {
                val candidate = File(dir, "${safeName}_$i.json")
                if (!candidate.exists()) {
                    file = candidate
                    break
                }
                i++
            }
        }

        val content = json.encodeToString(data)
        file.writeText(content)
        return file
    }

    fun exportWorldSelectionAsTemplate(
        world: World,
        origin: BlockPos,
        corner: BlockPos,
        structureId: String,
        displayName: String? = null,
        includeAir: Boolean = false,
        includeTileNbtConstraints: Boolean = false,
    ): StructureData {
        val minX = minOf(origin.x, corner.x)
        val minY = minOf(origin.y, corner.y)
        val minZ = minOf(origin.z, corner.z)
        val maxX = maxOf(origin.x, corner.x)
        val maxY = maxOf(origin.y, corner.y)
        val maxZ = maxOf(origin.z, corner.z)

        val elements = ArrayList<StructurePatternElementData>()

        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val worldPos = BlockPos(x, y, z)
                    if (worldPos == origin) {
                        // Reserve controller/origin position.
                        continue
                    }

                    val state = world.getBlockState(worldPos)
                    val block = state.block
                    if (!includeAir && block === Blocks.AIR) continue

                    val id = block.registryName?.toString() ?: continue
                    @Suppress("DEPRECATION")
                    val meta = block.getMetaFromState(state)

                    val rel = worldPos.subtract(origin)

                    val nbtConstraints = if (includeTileNbtConstraints) {
                        world.getTileEntity(worldPos)
                            ?.let { te ->
                                val tag = NBTTagCompound()
                                te.writeToNBT(tag)
                                nbtCompoundToShallowStringMap(tag)
                            }
                            ?.takeIf { it.isNotEmpty() }
                    } else {
                        null
                    }

                    elements.add(
                        StructurePatternElementData(
                            pos = BlockPosData(rel.x, rel.y, rel.z),
                            blockId = id,
                            meta = meta,
                            nbt = nbtConstraints,
                            alternatives = emptyList()
                        )
                    )
                }
            }
        }

        // Keep output stable for diffs.
        elements.sortWith(
            compareBy<StructurePatternElementData>({ it.pos.y }, { it.pos.z }, { it.pos.x }, { it.blockId }, { it.meta })
        )

        return StructureData(
            id = structureId,
            name = displayName,
            type = "template",
            offset = BlockPosData(0, 0, 0),
            hideWorldBlocks = false,
            pattern = elements,
            validators = emptyList(),
            children = emptyList(),
        )
    }

    /**
     * Best-effort conversion for MMCE-like NBT matching into PM shallow constraints.
     * Only exports root keys.
     */
    fun nbtCompoundToShallowStringMap(tag: NBTTagCompound): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for (k in tag.keySet) {
            val base: NBTBase = tag.getTag(k) ?: continue
            val v = when (base) {
                is NBTTagString -> tag.getString(k)
                else -> base.toString()
            }
            out[k] = v
        }
        return out
    }

    data class Descriptor(val blockId: String, val meta: Int)

    data class ElementOption(val descriptor: Descriptor, val nbt: Map<String, String>?)

    fun parseMmceDescriptor(raw: String): Descriptor {
        // MM/MMCE format: "modid:block@meta"; meta optional.
        val idx = raw.indexOf('@')
        return if (idx != -1 && idx < raw.length - 1) {
            val blockId = raw.substring(0, idx)
            val meta = raw.substring(idx + 1).toInt()
            Descriptor(blockId, meta)
        } else {
            Descriptor(raw, 0)
        }
    }

    fun toPatternElementData(
        relPos: BlockPos,
        options: List<Descriptor>,
        nbtConstraints: Map<String, String>?,
    ): StructurePatternElementData {
        require(options.isNotEmpty()) { "options must not be empty" }
        val base = options[0]
        val alternatives = if (options.size <= 1) {
            emptyList()
        } else {
            options.drop(1).map { opt ->
                StructurePatternAlternativeData(
                    blockId = opt.blockId,
                    meta = opt.meta,
                    nbt = null
                )
            }
        }

        return StructurePatternElementData(
            pos = BlockPosData(relPos.x, relPos.y, relPos.z),
            blockId = base.blockId,
            meta = base.meta,
            nbt = nbtConstraints,
            alternatives = alternatives
        )
    }
}
