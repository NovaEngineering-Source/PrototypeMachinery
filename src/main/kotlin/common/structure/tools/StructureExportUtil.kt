package github.kasuminova.prototypemachinery.common.structure.tools

import github.kasuminova.prototypemachinery.common.structure.serialization.BlockPosData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructureData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructurePatternAlternativeData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructurePatternElementData
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagString
import net.minecraft.util.EnumFacing
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
        val min = BlockPos(minOf(origin.x, corner.x), minOf(origin.y, corner.y), minOf(origin.z, corner.z))
        val max = BlockPos(maxOf(origin.x, corner.x), maxOf(origin.y, corner.y), maxOf(origin.z, corner.z))
        return exportWorldBoxAsTemplate(
            world = world,
            selectionMin = min,
            selectionMax = max,
            exportOrigin = origin,
            structureId = structureId,
            displayName = displayName,
            includeAir = includeAir,
            includeTileNbtConstraints = includeTileNbtConstraints,
            rotationWorldToTemplate = { it },
        )
    }

    /**
     * Export a world selection box as a template structure.
     *
     * - The selection region is defined by [selectionMin]..[selectionMax] (inclusive).
     * - Relative positions are computed against [exportOrigin] (which may be inside the box).
     * - [rotationWorldToTemplate] rotates both relPos and IBlockState facings from world space into template space.
     *
     * This is the cornerstone for scanner “导出转向 / 归北 / 自动原点” workflows.
     */
    fun exportWorldBoxAsTemplate(
        world: World,
        selectionMin: BlockPos,
        selectionMax: BlockPos,
        exportOrigin: BlockPos,
        structureId: String,
        displayName: String? = null,
        includeAir: Boolean = false,
        includeTileNbtConstraints: Boolean = false,
        rotationWorldToTemplate: (EnumFacing) -> EnumFacing = { it },
    ): StructureData {
        val minX = minOf(selectionMin.x, selectionMax.x)
        val minY = minOf(selectionMin.y, selectionMax.y)
        val minZ = minOf(selectionMin.z, selectionMax.z)
        val maxX = maxOf(selectionMin.x, selectionMax.x)
        val maxY = maxOf(selectionMin.y, selectionMax.y)
        val maxZ = maxOf(selectionMin.z, selectionMax.z)

        val elements = ArrayList<StructurePatternElementData>()

        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    val worldPos = BlockPos(x, y, z)
                    if (worldPos == exportOrigin) {
                        // Reserve controller/origin position.
                        continue
                    }

                    val rawState = world.getBlockState(worldPos)
                    val block = rawState.block
                    if (!includeAir && block === Blocks.AIR) continue

                    val id = block.registryName?.toString() ?: continue

                    val relWorld = worldPos.subtract(exportOrigin)
                    val relTemplate = github.kasuminova.prototypemachinery.impl.machine.structure.StructureUtils.rotatePos(
                        relWorld,
                        rotationWorldToTemplate
                    )

                    val state = rotateState(rawState, rotationWorldToTemplate)
                    @Suppress("DEPRECATION")
                    val meta = block.getMetaFromState(state)

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
                            pos = BlockPosData(relTemplate.x, relTemplate.y, relTemplate.z),
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

    private fun rotateState(state: IBlockState, rotation: (EnumFacing) -> EnumFacing): IBlockState {
        var newState = state
        for (prop in state.propertyKeys) {
            // Handle EnumFacing properties (Direction)
            if (prop.valueClass === EnumFacing::class.java) {
                @Suppress("UNCHECKED_CAST")
                val directionProp = prop as IProperty<EnumFacing>
                val current = state.getValue(directionProp)
                val newFacing = rotation(current)
                if (directionProp.allowedValues.contains(newFacing)) {
                    newState = newState.withProperty(directionProp, newFacing)
                }
            }

            // Handle EnumFacing.Axis properties (Axis)
            if (prop.valueClass === EnumFacing.Axis::class.java) {
                @Suppress("UNCHECKED_CAST")
                val axisProp = prop as IProperty<EnumFacing.Axis>
                val currentAxis = state.getValue(axisProp)

                val sample = when (currentAxis) {
                    EnumFacing.Axis.X -> EnumFacing.EAST
                    EnumFacing.Axis.Y -> EnumFacing.UP
                    EnumFacing.Axis.Z -> EnumFacing.NORTH
                }
                val newAxis = rotation(sample).axis
                if (axisProp.allowedValues.contains(newAxis)) {
                    newState = newState.withProperty(axisProp, newAxis)
                }
            }
        }
        return newState
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
