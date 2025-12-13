package github.kasuminova.prototypemachinery.api.machine.attribute

/**
 * # MachineAttributeModifier - Modifier
 * # MachineAttributeModifier - 属性修改器
 *
 * A modifier changes a [MachineAttributeInstance] value.
 *
 * 修改器用于改变 [MachineAttributeInstance] 的最终数值。
 *
 * ## Tracing / 追踪来源
 *
 * [adder] can be used to trace who/what added this modifier (debugging & reverse lookup).
 *
 * [adder] 可用于追踪“是谁/什么”添加了该修改器（调试、反向追溯）。
 *
 * Note: when persisted to NBT, `adder` is not saved.
 *
 * 注意：当写入 NBT 时，`adder` 不保存。
 */
public interface MachineAttributeModifier {

    public val id: String

    public val amount: Double

    /** How this modifier affects the value. / 修改器的运算类型。 */
    public val operation: Operation

    /** Who/what added this modifier (debugging & reverse lookup). / 添加者信息（用于反向追踪与调试）。 */
    public val adder: Any?
        get() = null

    public fun apply(base: Double, current: Double): Double

    public enum class Operation {
        /** Add `amount` to the current value. / 将 `amount` 加到当前值上。 */
        ADDITION,

        /** Add `base * amount` to the current value. / 将 `base * amount` 加到当前值上。 */
        MULTIPLY_BASE,

        /** Multiply current value by `(1 + amount)`. / 将当前值乘以 `(1 + amount)`。 */
        MULTIPLY_TOTAL,
    }

}