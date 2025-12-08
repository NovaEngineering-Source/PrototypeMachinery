package github.kasuminova.prototypemachinery.integration.crafttweaker

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import net.minecraft.util.ResourceLocation

/**
 * Builder for creating machine types from CraftTweaker scripts (preview helper).
 * 用于在 CraftTweaker/ZenScript 中构建机器类型的预览期工具。
 */
public class CraftTweakerMachineTypeBuilder(
    private val id: ResourceLocation
) {

    private var name: String = id.toString()
    private var structure: MachineStructure? = null
    private val componentTypes: MutableSet<MachineComponentType<*>> = mutableSetOf()

    /**
     * Set display name for scripts.
     * 设置脚本侧展示名称。
     */
    public fun name(name: String): CraftTweakerMachineTypeBuilder {
        this.name = name
        return this
    }

    /**
     * Provide structure definition used for validation/placement.
     * 设置结构定义，用于校验/放置。
     */
    public fun structure(structure: MachineStructure): CraftTweakerMachineTypeBuilder {
        this.structure = structure
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
        val structure = this.structure
            ?: throw IllegalStateException("Structure is required for machine type $id")

        return CraftTweakerMachineTypeImpl(
            id = id,
            name = name,
            structure = structure,
            componentTypes = componentTypes.toSet()
        )
    }

}

/**
 * Internal implementation of ICraftTweakerMachineType.
 */
private data class CraftTweakerMachineTypeImpl(
    override val id: ResourceLocation,
    override val name: String,
    override val structure: MachineStructure,
    override val componentTypes: Set<MachineComponentType<*>>
) : ICraftTweakerMachineType
