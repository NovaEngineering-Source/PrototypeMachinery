package github.kasuminova.prototypemachinery.integration.crafttweaker

import github.kasuminova.prototypemachinery.api.machine.MachineTypeExecutionModeDefaults
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeType
import github.kasuminova.prototypemachinery.api.machine.attribute.MachineTypeAttributeDefaults
import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.component.type.ZSDataComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.scheduler.ExecutionMode
import github.kasuminova.prototypemachinery.impl.machine.structure.StructureRegistryImpl
import net.minecraft.util.ResourceLocation

/**
 * Builder for creating machine types from CraftTweaker scripts (preview helper).
 * 用于在 CraftTweaker/ZenScript 中构建机器类型的预览期工具。
 */
public class CraftTweakerMachineTypeBuilder(
    private val id: ResourceLocation
) {

    private var name: String = id.toString()
    private var structureProvider: (() -> MachineStructure)? = null

    // Default component types include ZSDataComponent for script data storage
    // 默认组件类型包含 ZSDataComponent 用于脚本数据存储
    private val componentTypes: MutableSet<MachineComponentType<*>> = mutableSetOf(ZSDataComponentType)

    // Recipe groups accepted by this machine type (used by FactoryRecipeScanningSystem).
    // 该机器类型可处理的配方组（FactoryRecipeScanningSystem 会依赖此字段）。
    private val recipeGroups: MutableSet<ResourceLocation> = linkedSetOf()
    private var controllerModel: ResourceLocation? = null

    // Default machine-level attribute bases.
    // 机器层默认属性 base 值（用于并行上限、速度倍率等）。
    private val defaultAttributeBases: MutableMap<MachineAttributeType, Double> = linkedMapOf()

    // Default execution mode for scheduling machine logic.
    // 机器逻辑调度默认执行模式。
    private var defaultExecutionMode: ExecutionMode? = null

    /**
     * Set display name for scripts.
     * 设置脚本侧展示名称。
     */
    public fun name(name: String): CraftTweakerMachineTypeBuilder {
        this.name = name
        return this
    }

    /**
     * Set custom model location for the controller block.
     * 设置控制器方块的自定义模型位置。
     */
    public fun controllerModel(modelLocation: ResourceLocation): CraftTweakerMachineTypeBuilder {
        this.controllerModel = modelLocation
        return this
    }

    /**
     * Provide structure definition used for validation/placement (direct reference).
     * 设置结构定义，用于校验/放置（直接引用）。
     */
    public fun structure(structure: MachineStructure): CraftTweakerMachineTypeBuilder {
        this.structureProvider = { structure }
        return this
    }

    /**
     * Provide structure definition by ID (lazy reference).
     * 通过 ID 设置结构定义（延迟引用）。
     * 
     * This variant uses lazy loading to avoid loading order issues.
     * The structure will be resolved from the registry when the machine type is first accessed.
     * 
     * 此变体使用延迟加载以避免加载顺序问题。
     * 结构将在首次访问机器类型时从注册表解析。
     * 
     * @param structureId The ID of the structure registered in the structure registry
     * @throws IllegalArgumentException when building if the structure ID is not found in registry
     */
    public fun structure(structureId: String): CraftTweakerMachineTypeBuilder {
        this.structureProvider = {
            StructureRegistryImpl.get(structureId)
                ?: throw IllegalArgumentException(
                    "Structure with ID '$structureId' not found in registry. " +
                            "Make sure the structure JSON file exists and is loaded before registering this machine type."
                )
        }
        return this
    }

    /**
     * Add a supported component type.
     * 添加一个支持的组件类型。
     */
    public fun addComponentType(componentType: MachineComponentType<*>): CraftTweakerMachineTypeBuilder {
        componentTypes.add(componentType)
        return this
    }

    /**
     * Adds a recipe group accepted by this machine type.
     *
     * 添加一个该机器类型可处理的配方组。
     *
     * The group id should be a ResourceLocation string, e.g. "mymod:my_group".
     */
    public fun addRecipeGroup(groupId: String): CraftTweakerMachineTypeBuilder {
        val id = ResourceLocation(groupId)
        recipeGroups.add(id)
        return this
    }

    /**
     * Adds multiple recipe groups.
     * 添加多个配方组。
     */
    public fun addRecipeGroups(groupIds: Iterable<String>): CraftTweakerMachineTypeBuilder {
        for (g in groupIds) {
            addRecipeGroup(g)
        }
        return this
    }

    /**
     * Set maximum number of concurrently running recipe processes on this machine.
     *
     * 设置该机器允许“同时运行”的配方进程数量上限。
     *
     * - 1 表示一次只能跑 1 个配方进程
     * - 2 表示可以同时跑 2 个不同配方/同配方的两个进程（由扫描系统决定）
     */
    public fun maxConcurrentProcesses(max: Int): CraftTweakerMachineTypeBuilder {
        require(max >= 1) { "maxConcurrentProcesses must be >= 1" }
        defaultAttributeBases[StandardMachineAttributes.MAX_CONCURRENT_PROCESSES] = max.toDouble()
        return this
    }

    /**
     * Set machine-level cap for per-process parallelism.
     *
     * 设置机器层面对“单个进程并行倍数”的上限。
     */
    public fun processParallelism(limit: Int): CraftTweakerMachineTypeBuilder {
        require(limit >= 1) { "processParallelism must be >= 1" }
        defaultAttributeBases[StandardMachineAttributes.PROCESS_PARALLELISM] = limit.toDouble()
        return this
    }

    /**
     * Set scheduling execution mode for this machine type.
     *
     * 设置该机器类型的逻辑执行线程模式。
     *
     * Supported values (case-insensitive):
     * - "MAIN_THREAD"
     * - "CONCURRENT"
     */
    public fun executionMode(mode: String): CraftTweakerMachineTypeBuilder {
        val normalized = mode.trim().replace('-', '_').uppercase()
        defaultExecutionMode = when (normalized) {
            "MAIN_THREAD", "MAIN", "SYNC" -> ExecutionMode.MAIN_THREAD
            "CONCURRENT", "ASYNC" -> ExecutionMode.CONCURRENT
            else -> throw IllegalArgumentException(
                "Unknown execution mode '$mode'. Expected MAIN_THREAD or CONCURRENT."
            )
        }
        return this
    }

    /** Convenience: force main thread execution. */
    public fun mainThread(): CraftTweakerMachineTypeBuilder {
        defaultExecutionMode = ExecutionMode.MAIN_THREAD
        return this
    }

    /** Convenience: force concurrent execution. */
    public fun concurrent(): CraftTweakerMachineTypeBuilder {
        defaultExecutionMode = ExecutionMode.CONCURRENT
        return this
    }

    /**
     * Build machine type wrapper for later registration.
     * 构建机器类型包装，用于后续注册。
     *
     * @throws IllegalStateException if required fields are not set
     */
    public fun build(): ICraftTweakerMachineType {
        val structureProvider = this.structureProvider
            ?: throw IllegalStateException("Structure is required for machine type $id. Use structure() method to set it.")

        return CraftTweakerMachineTypeImpl(
            id = id,
            name = name,
            structureProvider = structureProvider,
            componentTypes = componentTypes.toSet(),
            recipeGroups = recipeGroups.toSet(),
            controllerModelLocation = controllerModel,
            defaultAttributeBases = defaultAttributeBases.toMap(),
            defaultExecutionMode = defaultExecutionMode ?: ExecutionMode.CONCURRENT
        )
    }

}

/**
 * Internal implementation of ICraftTweakerMachineType.
 * 
 * Uses lazy loading for structure to avoid initialization order issues.
 * 使用延迟加载结构以避免初始化顺序问题。
 */
private class CraftTweakerMachineTypeImpl(
    override val id: ResourceLocation,
    override val name: String,
    private val structureProvider: () -> MachineStructure,
    override val componentTypes: Set<MachineComponentType<*>>,
    override val recipeGroups: Set<ResourceLocation>,
    override val controllerModelLocation: ResourceLocation?,
    override val defaultAttributeBases: Map<MachineAttributeType, Double>,
    override val defaultExecutionMode: ExecutionMode
) : ICraftTweakerMachineType, MachineTypeAttributeDefaults, MachineTypeExecutionModeDefaults {

    /**
     * Lazily loaded structure instance.
     * 延迟加载的结构实例。
     */
    override val structure: MachineStructure
        get() {
            try {
                return structureProvider()
            } catch (e: Throwable) {
                throw IllegalStateException(
                    "Failed to resolve structure for machine type '$id': ${e.message}",
                    e
                )
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CraftTweakerMachineTypeImpl) return false

        if (id != other.id) return false
        if (name != other.name) return false
        if (componentTypes != other.componentTypes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + componentTypes.hashCode()
        return result
    }

    override fun toString(): String {
        return "CraftTweakerMachineType(id=$id, name='$name', componentTypes=$componentTypes)"
    }
}
