package github.kasuminova.prototypemachinery.integration.crafttweaker

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
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
    private val componentTypes: MutableSet<MachineComponentType<*>> = mutableSetOf()
    private var controllerModel: ResourceLocation? = null

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
            controllerModelLocation = controllerModel
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
    override val controllerModelLocation: ResourceLocation?
) : ICraftTweakerMachineType {
    
    /**
     * Lazily loaded structure instance.
     * 延迟加载的结构实例。
     */
    override val structure: MachineStructure by lazy {
        try {
            structureProvider()
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
