package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.integration.crafttweaker.CraftTweakerMachineTypeBuilder
import github.kasuminova.prototypemachinery.integration.crafttweaker.ICraftTweakerMachineType
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript wrapper for MachineTypeBuilder (preview-facing helper).
 * 面向预览的 MachineTypeBuilder ZenScript 包装。
 */
@ZenClass("mods.prototypemachinery.MachineTypeBuilder")
@ZenRegister
public class ZenMachineTypeBuilder(
    private val builder: CraftTweakerMachineTypeBuilder
) {

    /**
     * Set display name for scripts.
     * 设置脚本侧展示名称。
     *
     * @return This builder for chaining / 链式返回
     */
    @ZenMethod
    public fun name(name: String): ZenMachineTypeBuilder {
        builder.name(name)
        return this
    }

    /**
     * Provide structure definition used for validation/placement (direct reference).
     * 设置结构定义，用于校验/放置（直接引用）。
     *
     * @param structure The structure instance to use
     * @return This builder for chaining / 链式返回
     */
    @ZenMethod
    public fun structure(structure: MachineStructure): ZenMachineTypeBuilder {
        builder.structure(structure)
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
     * @return This builder for chaining / 链式返回
     */
    @ZenMethod
    public fun structure(structureId: String): ZenMachineTypeBuilder {
        builder.structure(structureId)
        return this
    }

    /**
     * Add a supported component type.
     * 添加一个支持的组件类型。
     *
     * @return This builder for chaining / 链式返回
     */
    @ZenMethod
    public fun addComponentType(componentType: MachineComponentType<*>): ZenMachineTypeBuilder {
        builder.addComponentType(componentType)
        return this
    }

    /**
     * Build the machine type (internal use; not for direct ZenScript call).
     * 构建机器类型（内部调用，不直接暴露给脚本）。
     */
    internal fun build(): ICraftTweakerMachineType = builder.build()

}
