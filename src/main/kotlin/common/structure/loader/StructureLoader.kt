package github.kasuminova.prototypemachinery.common.structure.loader

import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import github.kasuminova.prototypemachinery.common.registry.StructureRegisterer
import github.kasuminova.prototypemachinery.common.structure.serialization.*
import github.kasuminova.prototypemachinery.impl.machine.structure.SliceStructure
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureRegistryImpl
import github.kasuminova.prototypemachinery.impl.machine.structure.TemplateStructure
import github.kasuminova.prototypemachinery.impl.machine.structure.pattern.SimpleStructurePattern
import github.kasuminova.prototypemachinery.impl.machine.structure.pattern.predicate.StatedBlockPredicate
import kotlinx.serialization.json.Json
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import java.io.File

/**
 * Loader for machine structure definitions from JSON files.
 * 从 JSON 文件加载机器结构定义的加载器。
 *
 * Reads JSON files from config/prototypemachinery/structures folder,
 * deserializes them using kotlinx.serialization, and converts to MachineStructure instances.
 *
 * 从 config/prototypemachinery/structures 文件夹读取 JSON 文件，
 * 使用 kotlinx.serialization 反序列化，并转换为 MachineStructure 实例。
 */
public object StructureLoader {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    // Default orientation: NORTH front, UP top
    // 默认朝向：NORTH 正面，UP 顶部
    private val DEFAULT_ORIENTATION = StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)

    // Cache for loaded structure data before resolving children
    // 在解析子结构之前缓存加载的结构数据
    private val structureDataCache = mutableMapOf<String, StructureData>()

    // Cache for converted structure instances to ensure reference equality
    // 缓存已转换的结构实例以确保引用相等
    private val structureInstanceCache = mutableMapOf<String, MachineStructure>()

    /**
     * Load all structure JSON files from config directory (PreInit phase).
     * 从配置目录加载所有结构 JSON 文件（PreInit 阶段）。
     *
     * This only loads and caches the JSON data without resolving block references.
     * Block resolution happens in PostInit when all blocks are registered.
     *
     * 此方法仅加载和缓存 JSON 数据，不解析方块引用。
     * 方块解析将在 PostInit 阶段进行，此时所有方块都已注册。
     *
     * @param event The PreInit event for logging and config directory access
     */
    public fun loadStructureData(event: FMLPreInitializationEvent) {
        val configDir = event.modConfigurationDirectory
        val structuresDir = File(configDir, "prototypemachinery/structures")

        if (!structuresDir.exists()) {
            structuresDir.mkdirs()
            event.modLog.info("Created structures directory at: ${structuresDir.absolutePath}")
        }

        val jsonFiles = structuresDir.listFiles { file ->
            file.isFile && file.extension.equals("json", ignoreCase = true)
        } ?: emptyArray()

        if (jsonFiles.isEmpty()) {
            event.modLog.info("No structure JSON files found in: ${structuresDir.absolutePath}")
            event.modLog.info("Copying example structures from resources...")
            copyExampleStructures(structuresDir, event)
            return
        }

        event.modLog.info("Loading ${jsonFiles.size} structure file(s) from: ${structuresDir.absolutePath}")

        // Load all JSON files and cache StructureData
        // 加载所有 JSON 文件并缓存 StructureData
        for (file in jsonFiles) {
            try {
                loadStructureDataFile(file, event)
            } catch (e: Exception) {
                event.modLog.error("Failed to load structure data from file: ${file.name}", e)
            }
        }

        event.modLog.info("Loaded ${structureDataCache.size} structure definition(s), waiting for PostInit to resolve blocks")
    }

    /**
     * Process loaded structure data and register structures (PostInit phase).
     * 处理已加载的结构数据并注册结构（PostInit 阶段）。
     *
     * This converts StructureData to MachineStructure instances with resolved block references.
     * All blocks from all mods are guaranteed to be registered at this point.
     *
     * 此方法将 StructureData 转换为包含已解析方块引用的 MachineStructure 实例。
     * 此时保证所有模组的方块都已注册。
     *
     * @param event The PostInit event for logging
     */
    public fun processStructures(event: FMLPostInitializationEvent) {
        if (structureDataCache.isEmpty()) {
            PrototypeMachinery.logger.info("No structure data to process")
            return
        }

        PrototypeMachinery.logger.info("Processing ${structureDataCache.size} structure(s) and resolving block references...")

        // Resolve children and create MachineStructure instances
        // 解析子结构并创建 MachineStructure 实例
        for ((id, structureData) in structureDataCache) {
            try {
                val structure = convertToMachineStructure(structureData, event)
                StructureRegisterer.queue(structure)
                PrototypeMachinery.logger.info("Queued structure '$id' for registration")
            } catch (e: Exception) {
                PrototypeMachinery.logger.error("Failed to convert structure data for '$id'", e)
            }
        }

        // Process the registration queue
        // 处理注册队列
        StructureRegisterer.processQueue(event)

        // Clear cache after processing
        // 处理完成后清除缓存
        structureDataCache.clear()
        structureInstanceCache.clear()

        PrototypeMachinery.logger.info("Structure processing completed")
    }

    /**
     * Load a single structure file and cache the data.
     * 加载单个结构文件并缓存数据。
     */
    private fun loadStructureDataFile(file: File, event: FMLPreInitializationEvent) {
        val jsonText = file.readText()
        val structureData = json.decodeFromString<StructureData>(jsonText)

        if (structureDataCache.containsKey(structureData.id)) {
            event.modLog.warn("Duplicate structure ID '${structureData.id}' in file: ${file.name}")
            return
        }

        structureDataCache[structureData.id] = structureData
        event.modLog.info("Loaded structure data '${structureData.id}' from file: ${file.name}")
    }

    /**
     * Convert StructureData to MachineStructure.
     * 将 StructureData 转换为 MachineStructure。
     *
     * Children are resolved by ID reference from the cache or registry.
     * Ensures reference equality for same structure IDs and detects circular dependencies.
     *
     * 子结构通过缓存或注册表中的 ID 引用解析。
     * 确保相同结构 ID 使用相同引用，并检测循环依赖。
     */
    private fun convertToMachineStructure(
        data: StructureData,
        event: FMLPostInitializationEvent,
        conversionPath: MutableSet<String> = mutableSetOf()
    ): MachineStructure {
        // Check for circular dependencies
        // 检查循环依赖
        if (data.id in conversionPath) {
            val cycle = conversionPath.joinToString(" -> ") + " -> ${data.id}"
            throw IllegalStateException("Circular dependency detected in structure hierarchy: $cycle")
        }

        // Return cached instance if already converted (reference equality)
        // 如果已转换则返回缓存实例（引用相等）
        structureInstanceCache[data.id]?.let { return it }

        // Add current structure to conversion path
        // 将当前结构添加到转换路径
        conversionPath.add(data.id)

        val offset = BlockPos(data.offset.x, data.offset.y, data.offset.z)
        val pattern = convertPattern(data.pattern)
        
        // Resolve child structures by ID
        // 通过 ID 解析子结构
        val children = data.children.mapNotNull { childId ->
            // First try to get from registry (already registered)
            // 首先尝试从注册表获取（已注册的）
            val existing = StructureRegistryImpl.get(childId)
            if (existing != null) {
                return@mapNotNull existing
            }
            
            // Then try to get from instance cache (being converted)
            // 然后尝试从实例缓存获取（正在转换的）
            structureInstanceCache[childId]?.let { return@mapNotNull it }
            
            // Finally try to convert from data cache (not yet converted)
            // 最后尝试从数据缓存转换（尚未转换的）
            val childData = structureDataCache[childId]
            if (childData != null) {
                return@mapNotNull convertToMachineStructure(childData, event, conversionPath)
            }
            
            PrototypeMachinery.logger.warn("Child structure '$childId' not found for structure '${data.id}'")
            null
        }

        // Remove from conversion path after processing children
        // 处理完子结构后从转换路径移除
        conversionPath.remove(data.id)

        // Create structure instance
        // 创建结构实例
        val structure = when (data.type.lowercase()) {
            "template" -> TemplateStructure(
                id = data.id,
                orientation = DEFAULT_ORIENTATION,
                offset = offset,
                pattern = pattern,
                validators = emptyList(), // TODO: Support validator deserialization
                children = children
            )
            "slice" -> {
                val minCount = data.minCount 
                    ?: throw IllegalArgumentException("Slice structure '${data.id}' must have 'minCount' field")
                val maxCount = data.maxCount
                    ?: throw IllegalArgumentException("Slice structure '${data.id}' must have 'maxCount' field")
                
                val sliceOffset = data.sliceOffset?.let { 
                    BlockPos(it.x, it.y, it.z) 
                } ?: BlockPos(0, 1, 0) // Default to upward offset
                
                SliceStructure(
                    id = data.id,
                    orientation = DEFAULT_ORIENTATION,
                    offset = offset,
                    pattern = pattern,
                    minCount = minCount,
                    maxCount = maxCount,
                    sliceOffset = sliceOffset,
                    validators = emptyList(),
                    children = children
                )
            }
            else -> throw IllegalArgumentException("Unknown structure type: ${data.type}")
        }

        // Cache the instance for reference equality
        // 缓存实例以确保引用相等
        structureInstanceCache[data.id] = structure

        return structure
    }

    /**
     * Convert pattern elements to StructurePattern.
     * 将模式元素转换为 StructurePattern。
     */
    private fun convertPattern(elements: List<StructurePatternElementData>): StructurePattern {
        val blocks = mutableMapOf<BlockPos, BlockPredicate>()

        for (element in elements) {
            val pos = BlockPos(element.pos.x, element.pos.y, element.pos.z)
            val blockId = ResourceLocation(element.blockId)
            val block = Block.REGISTRY.getObject(blockId)

            @Suppress("DEPRECATION")
            val blockState = block.getStateFromMeta(element.meta)
            val predicate = StatedBlockPredicate(blockState)

            blocks[pos] = predicate
        }

        return SimpleStructurePattern(blocks)
    }

    /**
     * Copy example structure files from resources to config directory.
     * 从资源文件复制示例结构文件到配置目录。
     */
    private fun copyExampleStructures(targetDir: File, event: FMLPreInitializationEvent) {
        val exampleFiles = listOf(
            "simple_machine.json",
            "slice_machine.json",
            "complex_machine.json",
            "child_structure.json",
            "parent_with_child.json",
            "README.md"
        )

        val examplesDir = File(targetDir, "examples")
        if (!examplesDir.exists()) {
            examplesDir.mkdirs()
        }

        var copiedCount = 0
        for (fileName in exampleFiles) {
            try {
                val resourcePath = "/assets/prototypemachinery/structures/examples/$fileName"
                val inputStream = StructureLoader::class.java.getResourceAsStream(resourcePath)
                
                if (inputStream != null) {
                    val targetFile = File(examplesDir, fileName)
                    inputStream.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    copiedCount++
                    event.modLog.info("Copied example structure: $fileName")
                } else {
                    event.modLog.warn("Example structure not found in resources: $fileName")
                }
            } catch (e: Exception) {
                event.modLog.error("Failed to copy example structure: $fileName", e)
            }
        }

        if (copiedCount > 0) {
            event.modLog.info("Copied $copiedCount example structure(s) to: ${examplesDir.absolutePath}")
            event.modLog.info("You can use these as templates for your own structures")
        }
    }

}
