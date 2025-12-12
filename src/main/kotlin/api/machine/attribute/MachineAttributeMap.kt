package github.kasuminova.prototypemachinery.api.machine.attribute

/**
 * # MachineAttributeMap - Attribute Container
 * # MachineAttributeMap - 属性容器
 *
 * Holds a set of [MachineAttributeInstance] keyed by [MachineAttributeType].
 * Usually there are **two levels** of maps:
 *
 * - Machine-level (baseline): [github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl]
 * - Process-level overlay: [github.kasuminova.prototypemachinery.impl.machine.attribute.OverlayMachineAttributeMapImpl]
 *
 * 保存一组以 [MachineAttributeType] 为 key 的 [MachineAttributeInstance]。
 * 通常会有 **两层**：
 *
 * - 机器层（基线）：[github.kasuminova.prototypemachinery.impl.machine.attribute.MachineAttributeMapImpl]
 * - 进程层 overlay：[github.kasuminova.prototypemachinery.impl.machine.attribute.OverlayMachineAttributeMapImpl]
 *
 * ## Serialization / 序列化
 *
 * This interface itself is data-only; serialization is implemented in concrete classes
 * and helpers (see `MachineAttributeNbt`).
 *
 * 本接口仅定义数据视图；序列化由具体实现与工具类承担（参见 `MachineAttributeNbt`）。
 */
public interface MachineAttributeMap {

    public val attributes: Map<MachineAttributeType, MachineAttributeInstance>

}