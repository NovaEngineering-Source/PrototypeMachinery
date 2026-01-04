package github.kasuminova.prototypemachinery.api.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.MachineType

/**
 * Optional extension for [MachineType] to provide default machine-level attribute base values.
 *
 * 可选扩展：为 [MachineType] 提供“机器层属性”的默认基础值。
 *
 * ## Notes / 说明
 *
 * - Defaults are applied when a [github.kasuminova.prototypemachinery.api.machine.MachineInstance] is created.
 * - Defaults only apply when the attribute is absent (i.e. they do NOT overwrite saved NBT).
 *
 * - 默认值在机器实例创建时应用。
 * - 仅在属性不存在时生效（不会覆盖已保存的 NBT）。
 */
public interface MachineTypeAttributeDefaults {

    /**
     * Default attribute base values for a machine instance.
     *
     * 机器实例的默认属性 base 值。
     */
    public val defaultAttributeBases: Map<MachineAttributeType, Double>
}
