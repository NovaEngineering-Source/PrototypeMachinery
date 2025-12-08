package github.kasuminova.prototypemachinery.integration.crafttweaker

import github.kasuminova.prototypemachinery.api.machine.MachineType
import github.kasuminova.prototypemachinery.integration.crafttweaker.wrapper.MachineTypeWrapper
import net.minecraft.util.ResourceLocation

/**
 * Bridge between CraftTweaker and the internal API (preview glue layer).
 * CraftTweaker 与内部 API 的桥接（预览用胶水层）。
 */
public interface ICraftTweakerBridge {

    /**
     * Convert CraftTweaker machine definition to internal type.
     * 将 CraftTweaker 机器定义转换为内部类型。
     */
    public fun toInternalMachineType(ctMachineType: ICraftTweakerMachineType): MachineType

    /**
     * Create a builder for a machine type by ID.
     * 通过 ID 创建机器类型构建器。
     */
    public fun createBuilder(id: ResourceLocation): CraftTweakerMachineTypeBuilder

    /**
     * Create a builder with modId + path convenience.
     * 使用 modId + path 创建构建器的便捷方法。
     */
    public fun createBuilder(modId: String, path: String): CraftTweakerMachineTypeBuilder =
        createBuilder(ResourceLocation(modId, path))

}

/**
 * Default implementation of the CraftTweaker bridge.
 * CraftTweaker 桥的默认实现。
 */
public object CraftTweakerBridge : ICraftTweakerBridge {

    override fun toInternalMachineType(ctMachineType: ICraftTweakerMachineType): MachineType {
        return MachineTypeWrapper(ctMachineType)
    }

    override fun createBuilder(id: ResourceLocation): CraftTweakerMachineTypeBuilder {
        return CraftTweakerMachineTypeBuilder(id)
    }

}
