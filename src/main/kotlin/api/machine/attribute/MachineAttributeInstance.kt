package github.kasuminova.prototypemachinery.api.machine.attribute

/**
 * # MachineAttributeInstance - Runtime Attribute Value
 * # MachineAttributeInstance - 运行时属性实例
 *
 * Represents a numeric attribute value at runtime, including:
 * - a [base] value
 * - a set of [modifiers]
 * - a computed [value]
 *
 * 表示运行时的数值属性，包括：
 * - [base] 基础值
 * - [modifiers] 修改器集合
 * - [value] 计算后的最终值
 *
 * ## Modifier order / 修改器应用顺序
 *
 * The canonical order is:
 * 1) ADDITION
 * 2) MULTIPLY_BASE
 * 3) MULTIPLY_TOTAL
 *
 * 统一顺序为：
 * 1) 加法
 * 2) 基础乘法（基于 base 的乘法增量）
 * 3) 总乘法（对当前结果乘以系数）
 */
public interface MachineAttributeInstance {

    public val attribute: MachineAttributeType

    public val modifiers: Map<String, MachineAttributeModifier>

    public var base: Double

    public val value: Double

    public fun addModifier(modifier: MachineAttributeModifier): Boolean

    public fun removeModifier(id: String): MachineAttributeModifier?

    public fun hasModifier(id: String): Boolean

    public fun getModifier(id: String): MachineAttributeModifier?

}