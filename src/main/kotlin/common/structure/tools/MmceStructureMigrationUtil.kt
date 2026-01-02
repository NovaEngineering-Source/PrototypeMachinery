package github.kasuminova.prototypemachinery.common.structure.tools

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import github.kasuminova.prototypemachinery.common.structure.serialization.BlockPosData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructureData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructurePatternElementData
import github.kasuminova.prototypemachinery.common.structure.serialization.StructureValidatorSpecData
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.init.Bootstrap
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.registry.ForgeRegistries
import java.io.File
import kotlinx.serialization.json.JsonPrimitive as KxJsonPrimitive

/**
 * Best-effort converter from MMCE (ModularMachinery-Community-Edition) machine JSON into PM structure JSON.
 *
 * 目标：帮助整合包作者快速把旧项目的“结构定义”迁移到 PM。
 *
 * 当前限制：
 * - dynamic-patterns 只能“物化”为固定尺寸（默认取 minSize），PM 本身不支持动态伸缩
 * - NBT 仅做非常浅的 JSON->string map（可选），不保证能 100% 匹配
 */
internal object MmceStructureMigrationUtil {

    private data class NbtCheck(
        val pos: BlockPos,
        val nbt: Map<String, String>
    )

    enum class DynamicPatternMode {
        IGNORE,
        /** Preserve MMCE's [minSize,maxSize] as PM slice's [minCount,maxCount]. */
        RANGE,
        /** Force slice count to MMCE minSize. */
        MIN,
        /** Force slice count to MMCE maxSize. */
        MAX,
        FIXED
    }

    data class MigrationResult(
        val structure: StructureData,
        val additionalStructures: List<StructureData> = emptyList(),
        val warnings: List<String>
    )

    fun loadVariableContext(machineryDir: File): Map<String, List<StructureExportUtil.Descriptor>> {
        val out = LinkedHashMap<String, List<StructureExportUtil.Descriptor>>()
        if (!machineryDir.exists()) return out

        machineryDir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".var.json", ignoreCase = true) }
            .forEach { file ->
                val root = runCatching {
                    JsonParser().parse(file.readText()).asJsonObject
                }.getOrNull() ?: return@forEach

                for ((key, value) in root.entrySet()) {
                    val list = parseDescriptorList(value) ?: continue
                    // MMCE uses a global map; later definitions win.
                    out[key] = list
                }
            }

        return out
    }

    fun migrateMachineJson(
        machineJsonFile: File,
        variableContext: Map<String, List<StructureExportUtil.Descriptor>>,
        outStructureId: String?,
        includeNbtConstraints: Boolean,
        dynamicPatternMode: DynamicPatternMode = DynamicPatternMode.RANGE,
        dynamicPatternFixedSize: Int? = null,
    ): MigrationResult {
        val warnings = ArrayList<String>()
        val root = JsonParser().parse(machineJsonFile.readText()).asJsonObject

        val registryName = root.getAsJsonPrimitive("registryname")?.asString?.takeIf { it.isNotBlank() }
            ?: root.getAsJsonPrimitive("registryName")?.asString?.takeIf { it.isNotBlank() }
            ?: run {
                warnings.add("Missing 'registryname' in ${machineJsonFile.name}; falling back to file name")
                machineJsonFile.nameWithoutExtension
            }

        val localizedName = root.getAsJsonPrimitive("localizedname")?.asString
            ?.takeIf { it.isNotBlank() }

        val parts = root.getAsJsonArray("parts")
            ?: throw IllegalArgumentException("MMCE machine JSON missing 'parts': ${machineJsonFile.absolutePath}")

        val structureId = StructureExportUtil.sanitizeId(outStructureId ?: "mmce_$registryName")

        val acc = LinkedHashMap<BlockPos, AccumulatedPart>()

        // Base parts.
        accumulateParts(
            out = acc,
            parts = parts,
            translation = BlockPos.ORIGIN,
            variableContext = variableContext,
            includeNbtConstraints = includeNbtConstraints,
            warnings = warnings,
            sourceLabel = "parts",
            machineFileName = machineJsonFile.name,
            skipControllerOrigin = true,
        )

        // Optional: modifiers often represent block swaps at the same coordinate; treat them as alternatives.
        root.getAsJsonArray("modifiers")?.let { modifiersArr ->
            accumulateParts(
                out = acc,
                parts = modifiersArr,
                translation = BlockPos.ORIGIN,
                variableContext = variableContext,
                includeNbtConstraints = includeNbtConstraints,
                warnings = warnings,
                sourceLabel = "modifiers",
                machineFileName = machineJsonFile.name,
                skipControllerOrigin = true,
            )
        }

        // Optional: dynamic patterns -> split into child structures (start template + slice + end template).
        val dynamicChildren = ArrayList<String>()
        val additionalStructures = ArrayList<StructureData>()
        val dynArr = root.getAsJsonArray("dynamic-patterns")
        if (dynArr != null) {
            if (dynamicPatternMode == DynamicPatternMode.IGNORE) {
                warnings.add("MMCE machine '$registryName' contains 'dynamic-patterns' (ignored by --no-dynamic).")
            } else {
                migrateDynamicPatternsAsChildren(
                    outChildren = dynamicChildren,
                    outAdditionalStructures = additionalStructures,
                    dynamicPatterns = dynArr,
                    parentStructureId = structureId,
                    variableContext = variableContext,
                    includeNbtConstraints = includeNbtConstraints,
                    warnings = warnings,
                    machineFileName = machineJsonFile.name,
                    registryName = registryName,
                    mode = dynamicPatternMode,
                    fixedSize = dynamicPatternFixedSize
                )
            }
        }

        val elements = acc.entries
            .asSequence()
            .filter { (pos, _) -> pos != BlockPos.ORIGIN }
            .map { (pos, ap) ->
                val sortedOptions = ap.options
                    .sortedWith(compareBy({ it.blockId }, { it.meta }))

                val nbtConstraints = ap.nbtConstraints
                val nbtForPredicate = if (sortedOptions.size <= 1) nbtConstraints else null
                if (sortedOptions.size > 1 && nbtConstraints != null) {
                    // Loader currently cannot handle alternatives + NBT constraints.
                    // Migrate such checks into a configurable validator instead.
                    // (validator list is assembled below)
                }

                StructureExportUtil.toPatternElementData(
                    relPos = pos,
                    options = sortedOptions,
                    nbtConstraints = nbtForPredicate
                )
            }
            .sortedWith(compareBy({ it.pos.y }, { it.pos.z }, { it.pos.x }, { it.blockId }, { it.meta }))
            .toList()

        // Collect NBT checks that could not be represented safely in predicates (alternatives + nbt).
        val tileNbtChecks = if (includeNbtConstraints) {
            acc.entries
                .asSequence()
                .filter { (pos, _) -> pos != BlockPos.ORIGIN }
                .mapNotNull { (pos, ap) ->
                    val nbt = ap.nbtConstraints ?: return@mapNotNull null
                    if (ap.options.size <= 1) return@mapNotNull null
                    NbtCheck(pos = pos, nbt = nbt)
                }
                .toList()
        } else {
            emptyList()
        }

        val validators: List<StructureValidatorSpecData> = if (tileNbtChecks.isNotEmpty()) {
            warnings.add(
                "${machineJsonFile.name}: migrated ${tileNbtChecks.size} NBT constraint(s) into validator 'prototypemachinery:tile_nbt' because alternatives+nbt is not supported in predicates"
            )
            listOf(buildTileNbtValidator(tileNbtChecks))
        } else {
            emptyList()
        }

        val structure = StructureData(
            id = structureId,
            name = localizedName,
            type = "template",
            offset = BlockPosData(0, 0, 0),
            hideWorldBlocks = false,
            pattern = elements,
            validators = validators,
            children = dynamicChildren,
        )

        return MigrationResult(structure, additionalStructures, warnings)
    }

    private data class AccumulatedPart(
        val options: LinkedHashSet<StructureExportUtil.Descriptor> = LinkedHashSet(),
        var nbtConstraints: Map<String, String>? = null,
    )

    private fun accumulateParts(
        out: MutableMap<BlockPos, AccumulatedPart>,
        parts: JsonArray,
        translation: BlockPos,
        variableContext: Map<String, List<StructureExportUtil.Descriptor>>,
        includeNbtConstraints: Boolean,
        warnings: MutableList<String>,
        sourceLabel: String,
        machineFileName: String,
        skipControllerOrigin: Boolean,
    ) {
        for (partEl in parts) {
            if (!partEl.isJsonObject) continue
            val part = partEl.asJsonObject

            val xs = readCoords(part, "x")
            val ys = readCoords(part, "y")
            val zs = readCoords(part, "z")

            val rawElements = part.get("elements")
            if (rawElements == null) {
                warnings.add("$machineFileName: $sourceLabel part missing 'elements'")
                continue
            }

            val optionDescriptors = resolveElementsToDescriptors(rawElements, variableContext)
            if (optionDescriptors.isEmpty()) {
                warnings.add("$machineFileName: $sourceLabel part has empty 'elements'")
                continue
            }

            val nbtConstraints = if (includeNbtConstraints && part.has("nbt") && part.get("nbt").isJsonObject) {
                jsonObjectToShallowStringMap(part.getAsJsonObject("nbt"))
                    .takeIf { it.isNotEmpty() }
            } else {
                null
            }

            for (x in xs) for (y in ys) for (z in zs) {
                val rel = BlockPos(x, y, z).add(translation)
                if (skipControllerOrigin && rel == BlockPos.ORIGIN) {
                    // Controller position is reserved in both MMCE and PM.
                    continue
                }

                val ap = out.getOrPut(rel) { AccumulatedPart() }
                ap.options.addAll(optionDescriptors)

                if (nbtConstraints != null) {
                    if (ap.nbtConstraints == null) {
                        ap.nbtConstraints = nbtConstraints
                    } else if (ap.nbtConstraints != nbtConstraints) {
                        warnings.add("$machineFileName: conflicting NBT constraints at $rel from $sourceLabel; keeping the first one")
                    }
                }
            }
        }
    }

    private fun migrateDynamicPatternsAsChildren(
        outChildren: MutableList<String>,
        outAdditionalStructures: MutableList<StructureData>,
        dynamicPatterns: JsonArray,
        parentStructureId: String,
        variableContext: Map<String, List<StructureExportUtil.Descriptor>>,
        includeNbtConstraints: Boolean,
        warnings: MutableList<String>,
        machineFileName: String,
        registryName: String,
        mode: DynamicPatternMode,
        fixedSize: Int?,
    ) {
        for (patternEl in dynamicPatterns) {
            if (!patternEl.isJsonObject) continue
            val obj = patternEl.asJsonObject

            val nameRaw = obj.getAsJsonPrimitive("name")?.asString?.takeIf { it.isNotBlank() } ?: "dyn"
            val name = StructureExportUtil.sanitizeId(nameRaw)
            val minSize = obj.getAsJsonPrimitive("minSize")?.asInt ?: 1
            val maxSize = obj.getAsJsonPrimitive("maxSize")?.asInt ?: minSize
            val safeMin = minSize.coerceAtLeast(1)
            val safeMax = maxSize.coerceAtLeast(safeMin)

            val startOffset = readOffset(obj, "structure-size-offset-start")
            val stepOffset = readOffset(obj, "structure-size-offset")

            // Optional explicit end offset (not found in our sample pack, but support it for completeness).
            // In absence, we place parts-end one step after the last slice.
            val endOffset = if (obj.has("structure-size-offset-end")) {
                readOffset(obj, "structure-size-offset-end")
            } else if (obj.has("structure_size_offset_end")) {
                readOffset(obj, "structure_size_offset_end")
            } else {
                stepOffset
            }

            val partsStart = obj.getAsJsonArray("parts-start")
                ?: obj.getAsJsonArray("parts_start")
            val parts = obj.getAsJsonArray("parts")
            val partsEnd = obj.getAsJsonArray("parts-end")
                ?: obj.getAsJsonArray("parts_end")

            if (parts == null) {
                warnings.add("$machineFileName: dynamic-pattern '$nameRaw' missing 'parts' (skipped)")
                continue
            }

            val startId = StructureExportUtil.sanitizeId("${parentStructureId}__dyn_${name}__start")
            val sliceId = StructureExportUtil.sanitizeId("${parentStructureId}__dyn_${name}__slice")
            val endId = StructureExportUtil.sanitizeId("${parentStructureId}__dyn_${name}__end")

            val startPattern = if (partsStart != null) {
                buildPatternFromParts(
                    parts = partsStart,
                    variableContext = variableContext,
                    includeNbtConstraints = includeNbtConstraints,
                    warnings = warnings,
                    sourceLabel = "dynamic-patterns[$nameRaw].parts-start",
                    machineFileName = machineFileName,
                )
            } else {
                PatternBuildResult.empty()
            }

            val slicePattern = buildPatternFromParts(
                parts = parts,
                variableContext = variableContext,
                includeNbtConstraints = includeNbtConstraints,
                warnings = warnings,
                sourceLabel = "dynamic-patterns[$nameRaw].parts",
                machineFileName = machineFileName,
            )

            val endPattern = if (partsEnd != null) {
                buildPatternFromParts(
                    parts = partsEnd,
                    variableContext = variableContext,
                    includeNbtConstraints = includeNbtConstraints,
                    warnings = warnings,
                    sourceLabel = "dynamic-patterns[$nameRaw].parts-end",
                    machineFileName = machineFileName,
                )
            } else {
                PatternBuildResult.empty()
            }

            val (sliceMin, sliceMax) = when (mode) {
                DynamicPatternMode.RANGE -> safeMin to safeMax
                DynamicPatternMode.MIN -> safeMin to safeMin
                DynamicPatternMode.MAX -> safeMax to safeMax
                DynamicPatternMode.FIXED -> {
                    val chosen = (fixedSize ?: safeMin).coerceIn(safeMin, safeMax)
                    if (fixedSize != null && (fixedSize < safeMin || fixedSize > safeMax)) {
                        warnings.add("$machineFileName: dynamic-pattern '$nameRaw' requested size=$fixedSize out of range [$safeMin,$safeMax]; clamped to $chosen")
                    }
                    chosen to chosen
                }

                DynamicPatternMode.IGNORE -> safeMin to safeMax
            }

            // start(template) -> slice(slice) -> end(template)
            // If parts-start is missing/empty, skip the start structure and apply startOffset directly on slice.offset.
            val hasStart = startPattern.pattern.isNotEmpty()

            val sliceChildren = if (endPattern.pattern.isNotEmpty()) listOf(endId) else emptyList()
            val sliceStructure = StructureData(
                id = sliceId,
                name = "${registryName}:$nameRaw:slice",
                type = "slice",
                offset = if (hasStart) {
                    BlockPosData(0, 0, 0)
                } else {
                    BlockPosData(startOffset.x, startOffset.y, startOffset.z)
                },
                hideWorldBlocks = false,
                pattern = slicePattern.pattern,
                validators = slicePattern.validators,
                children = sliceChildren,
                minCount = sliceMin,
                maxCount = sliceMax,
                sliceOffset = BlockPosData(stepOffset.x, stepOffset.y, stepOffset.z),
            )

            val endStructure = if (endPattern.pattern.isNotEmpty()) {
                StructureData(
                    id = endId,
                    name = "${registryName}:$nameRaw:end",
                    type = "template",
                    // end is anchored at the last slice; apply the provided end offset (default: one step).
                    offset = BlockPosData(endOffset.x, endOffset.y, endOffset.z),
                    hideWorldBlocks = false,
                    pattern = endPattern.pattern,
                    validators = endPattern.validators,
                    children = emptyList(),
                )
            } else {
                null
            }

            if (hasStart) {
                val startStructure = StructureData(
                    id = startId,
                    name = "${registryName}:$nameRaw:start",
                    type = "template",
                    offset = BlockPosData(startOffset.x, startOffset.y, startOffset.z),
                    hideWorldBlocks = false,
                    pattern = startPattern.pattern,
                    validators = startPattern.validators,
                    children = listOf(sliceId),
                )

                outChildren.add(startId)
                outAdditionalStructures.add(startStructure)
            } else {
                outChildren.add(sliceId)
            }

            outAdditionalStructures.add(sliceStructure)
            if (endStructure != null) outAdditionalStructures.add(endStructure)

            warnings.add(
                if (hasStart) {
                    "$machineFileName: dynamic-pattern '$nameRaw' migrated as start/template + slice(min=$sliceMin,max=$sliceMax,step=$stepOffset) + end/template"
                } else {
                    "$machineFileName: dynamic-pattern '$nameRaw' has no parts-start; migrated as slice(offset=$startOffset,min=$sliceMin,max=$sliceMax,step=$stepOffset) + end/template"
                }
            )
        }
    }

    private data class PatternBuildResult(
        val pattern: List<StructurePatternElementData>,
        val validators: List<StructureValidatorSpecData>,
    ) {
        companion object {
            fun empty(): PatternBuildResult = PatternBuildResult(emptyList(), emptyList())
        }
    }

    private fun buildPatternFromParts(
        parts: JsonArray,
        variableContext: Map<String, List<StructureExportUtil.Descriptor>>,
        includeNbtConstraints: Boolean,
        warnings: MutableList<String>,
        sourceLabel: String,
        machineFileName: String,
    ): PatternBuildResult {
        val local = LinkedHashMap<BlockPos, AccumulatedPart>()
        accumulateParts(
            out = local,
            parts = parts,
            translation = BlockPos.ORIGIN,
            variableContext = variableContext,
            includeNbtConstraints = includeNbtConstraints,
            warnings = warnings,
            sourceLabel = sourceLabel,
            machineFileName = machineFileName,
            // This pattern will be offset by parent/child offsets; do NOT drop local origin.
            skipControllerOrigin = false,
        )

        val tileNbtChecks = ArrayList<NbtCheck>()
        val pattern = local.entries
            .asSequence()
            .map { (pos, ap) ->
                val sortedOptions = ap.options.sortedWith(compareBy({ it.blockId }, { it.meta }))
                val nbtConstraints = ap.nbtConstraints

                val nbtForPredicate = if (sortedOptions.size <= 1) nbtConstraints else null
                if (includeNbtConstraints && sortedOptions.size > 1 && nbtConstraints != null) {
                    tileNbtChecks.add(NbtCheck(pos = pos, nbt = nbtConstraints))
                }

                StructureExportUtil.toPatternElementData(
                    relPos = pos,
                    options = sortedOptions,
                    nbtConstraints = nbtForPredicate
                )
            }
            .sortedWith(compareBy({ it.pos.y }, { it.pos.z }, { it.pos.x }, { it.blockId }, { it.meta }))
            .toList()

        val validators: List<StructureValidatorSpecData> = if (tileNbtChecks.isNotEmpty()) {
            warnings.add(
                "$machineFileName: $sourceLabel migrated ${tileNbtChecks.size} NBT constraint(s) into validator 'prototypemachinery:tile_nbt' because alternatives+nbt is not supported in predicates"
            )
            listOf(buildTileNbtValidator(tileNbtChecks))
        } else {
            emptyList()
        }

        return PatternBuildResult(pattern = pattern, validators = validators)
    }

    private fun buildTileNbtValidator(checks: List<NbtCheck>): StructureValidatorSpecData {
        val params = buildJsonObject {
            put("checks", buildJsonArray {
                for (c in checks) {
                    add(
                        buildJsonObject {
                            put(
                                "pos",
                                buildJsonObject {
                                    put("x", KxJsonPrimitive(c.pos.x))
                                    put("y", KxJsonPrimitive(c.pos.y))
                                    put("z", KxJsonPrimitive(c.pos.z))
                                }
                            )
                            put(
                                "nbt",
                                buildJsonObject {
                                    for ((k, v) in c.nbt) {
                                        put(k, KxJsonPrimitive(v))
                                    }
                                }
                            )
                        }
                    )
                }
            })
        }

        return StructureValidatorSpecData(
            id = "prototypemachinery:tile_nbt",
            params = params
        )
    }

    private fun readOffset(obj: JsonObject, key: String): BlockPos {
        val off = obj.get(key)
        if (off == null || !off.isJsonObject) return BlockPos.ORIGIN
        val o = off.asJsonObject
        val x = o.getAsJsonPrimitive("x")?.asInt ?: 0
        val y = o.getAsJsonPrimitive("y")?.asInt ?: 0
        val z = o.getAsJsonPrimitive("z")?.asInt ?: 0
        return BlockPos(x, y, z)
    }

    private fun readCoords(part: JsonObject, key: String): List<Int> {
        val el = part.get(key) ?: return listOf(0)
        return when {
            el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> listOf(el.asInt)
            el.isJsonArray -> {
                val arr = el.asJsonArray
                if (arr.size() == 0) listOf(0)
                else arr.mapNotNull { e ->
                    if (e.isJsonPrimitive && e.asJsonPrimitive.isNumber) e.asInt else null
                }.ifEmpty { listOf(0) }
            }

            else -> listOf(0)
        }
    }

    private fun parseDescriptorList(value: JsonElement): List<StructureExportUtil.Descriptor>? {
        return when {
            value.isJsonPrimitive && value.asJsonPrimitive.isString ->
                parseMmceDescriptorExpanded(value.asString)

            value.isJsonArray -> {
                val arr: JsonArray = value.asJsonArray
                val list = ArrayList<StructureExportUtil.Descriptor>(arr.size())
                for (e in arr) {
                    if (!e.isJsonPrimitive || !e.asJsonPrimitive.isString) continue
                    list.addAll(parseMmceDescriptorExpanded(e.asString))
                }
                list.takeIf { it.isNotEmpty() }
            }

            else -> null
        }
    }

    private fun resolveElementsToDescriptors(
        raw: JsonElement,
        variableContext: Map<String, List<StructureExportUtil.Descriptor>>
    ): List<StructureExportUtil.Descriptor> {
        val out = LinkedHashSet<StructureExportUtil.Descriptor>()

        fun addOne(s: String) {
            val varList = variableContext[s]
            if (varList != null) {
                out.addAll(varList)
            } else {
                out.addAll(parseMmceDescriptorExpanded(s))
            }
        }

        when {
            raw.isJsonPrimitive && raw.asJsonPrimitive.isString -> addOne(raw.asString)
            raw.isJsonArray -> {
                for (e in raw.asJsonArray) {
                    if (e.isJsonPrimitive && e.asJsonPrimitive.isString) {
                        addOne(e.asString)
                    }
                }
            }
        }

        return out.toList()
    }

    /**
     * MMCE descriptor format:
     * - "modid:block@meta" => exact meta
     * - "modid:block" => matches multiple metas in MMCE; here we expand to alternatives.
     */
    private fun parseMmceDescriptorExpanded(raw: String): List<StructureExportUtil.Descriptor> {
        val idx = raw.indexOf('@')
        if (idx != -1 && idx < raw.length - 1) {
            val blockId = raw.substring(0, idx)
            val meta = raw.substring(idx + 1).toIntOrNull() ?: 0
            return listOf(StructureExportUtil.Descriptor(blockId, meta))
        }

        // No meta: expand metas from the registered block.
        // 注意：单元测试环境下 Minecraft/Forge Registries 通常没有完成 Bootstrap；此时访问 ForgeRegistries 会触发崩溃。
        if (!isBootstrapReady()) {
            return listOf(StructureExportUtil.Descriptor(raw, 0))
        }

        val rl = runCatching { ResourceLocation(raw) }.getOrNull()
            ?: return listOf(StructureExportUtil.Descriptor(raw, 0))

        val block = ForgeRegistries.BLOCKS.getValue(rl)
            ?: return listOf(StructureExportUtil.Descriptor(raw, 0))

        val metas = LinkedHashSet<Int>()
        @Suppress("DEPRECATION")
        for (state in block.blockState.validStates) {
            metas.add(block.getMetaFromState(state))
        }

        if (metas.isEmpty()) return listOf(StructureExportUtil.Descriptor(raw, 0))
        return metas.map { m -> StructureExportUtil.Descriptor(raw, m) }
    }

    private fun isBootstrapReady(): Boolean {
        return runCatching {
            Bootstrap.isRegistered()
        }.getOrDefault(false)
    }

    private fun jsonObjectToShallowStringMap(obj: JsonObject): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((k, v) in obj.entrySet()) {
            val str = when {
                v.isJsonNull -> "null"
                v.isJsonPrimitive -> v.asJsonPrimitive.asString
                else -> v.toString()
            }
            out[k] = str
        }
        return out
    }
}
