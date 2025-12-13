package github.kasuminova.prototypemachinery.integration.crafttweaker

import net.minecraft.util.ResourceLocation

/**
 * Bridge between CraftTweaker and the internal API (preview glue layer).
 * CraftTweaker 与内部 API 的桥接（预览用胶水层）。
 */
public interface ICraftTweakerBridge {

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

    override fun createBuilder(id: ResourceLocation): CraftTweakerMachineTypeBuilder {
        return CraftTweakerMachineTypeBuilder(id)
    }

}
