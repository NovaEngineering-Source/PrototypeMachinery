package github.kasuminova.prototypemachinery.integration.crafttweaker

import github.kasuminova.prototypemachinery.api.machine.component.MachineComponentType
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import net.minecraft.util.ResourceLocation

/**
 * CraftTweaker wrapper for machine type registration (preview wiring only).
 * CraftTweaker 机器类型注册的包装器（预览用的对接层）。
 */
public interface ICraftTweakerMachineType {

    /**
     * Unique identifier of this machine type.
     * 机器类型的唯一 ID。
     */
    public val id: ResourceLocation

    /**
     * Display name shown to players.
     * 对玩家展示的名称。
     */
    public val name: String

    /**
     * Structure definition used for validation/placement.
     * 用于校验/放置的结构定义。
     */
    public val structure: MachineStructure

    /**
     * Component types this machine supports.
     * 该机器支持的组件类型集合。
     */
    public val componentTypes: Set<MachineComponentType<*>>

}
