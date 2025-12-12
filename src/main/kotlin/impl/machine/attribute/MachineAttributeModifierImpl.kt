package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier

/**
 * Default modifier implementation.
 * 默认修改器实现。
 *
 * ## Semantics / 语义
 *
 * - ADDITION: `current + amount`
 * - MULTIPLY_BASE: `current + base * amount`
 * - MULTIPLY_TOTAL: `current * (1 + amount)`
 *
 * - 加法：`current + amount`
 * - 基础乘法：`current + base * amount`
 * - 总乘法：`current * (1 + amount)`
 *
 * [adder] is for tracing/debugging only.
 * [adder] 仅用于追踪/调试。
 */
public data class MachineAttributeModifierImpl(
    override val id: String,
    override val amount: Double,
    override val operation: MachineAttributeModifier.Operation,
    override val adder: Any? = null,
) : MachineAttributeModifier {

    override fun apply(base: Double, current: Double): Double {
        return when (operation) {
            MachineAttributeModifier.Operation.ADDITION -> current + amount
            MachineAttributeModifier.Operation.MULTIPLY_BASE -> current + (base * amount)
            MachineAttributeModifier.Operation.MULTIPLY_TOTAL -> current * (1.0 + amount)
        }
    }

    public companion object {
        @JvmStatic
        public fun addition(id: String, amount: Double, adder: Any? = null): MachineAttributeModifierImpl {
            return MachineAttributeModifierImpl(id, amount, MachineAttributeModifier.Operation.ADDITION, adder)
        }

        @JvmStatic
        public fun multiplyBase(id: String, amount: Double, adder: Any? = null): MachineAttributeModifierImpl {
            return MachineAttributeModifierImpl(id, amount, MachineAttributeModifier.Operation.MULTIPLY_BASE, adder)
        }

        @JvmStatic
        public fun multiplyTotal(id: String, amount: Double, adder: Any? = null): MachineAttributeModifierImpl {
            return MachineAttributeModifierImpl(id, amount, MachineAttributeModifier.Operation.MULTIPLY_TOTAL, adder)
        }
    }

}
