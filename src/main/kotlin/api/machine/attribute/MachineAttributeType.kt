package github.kasuminova.prototypemachinery.api.machine.attribute

import net.minecraft.util.ResourceLocation

/**
 * # MachineAttributeType - Attribute Definition
 * # MachineAttributeType - 机械属性定义
 *
 * Defines a numeric attribute that can influence machine or recipe behavior
 * (e.g., speed, efficiency, energy consumption multiplier).
 *
 * 定义可影响机械或配方行为的数值属性（如速度、效率、能耗倍数）。
 *
 * ## Examples / 示例
 * - SPEED, EFFICIENCY, ENERGY_USAGE, PARALLELISM
 *
 * ## Related Classes / 相关类
 * - [MachineAttributeInstance] - Holds runtime value and modifiers
 * - [MachineAttributeModifier] - Modifies attribute values
 * - [MachineAttributeMap] - Container for attributes per machine/process
 *
 * ## Registry / 注册表
 *
 * There is currently no global attribute registry in the framework.
 * Built-in attributes live in [StandardMachineAttributes].
 *
 * 当前框架尚未提供全局属性注册表。
 * 内置属性位于 [StandardMachineAttributes]。
 */
public interface MachineAttributeType {

    /** Unique ID for this attribute / 此属性的唯一 ID */
    public val id: ResourceLocation

    /** Human-readable name (localizable) / 可本地化的人类可读名称 */
    public val name: String

}