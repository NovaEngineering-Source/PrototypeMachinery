package github.kasuminova.prototypemachinery.impl.machine.attribute

import github.kasuminova.prototypemachinery.api.machine.attribute.MachineAttributeModifier

public data class MachineAttributeModifierImpl(
    override val id: String,
    override val amount: Double,
    public val operation: MachineAttributeModifier.Operation
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
        public fun addition(id: String, amount: Double): MachineAttributeModifierImpl {
            return MachineAttributeModifierImpl(id, amount, MachineAttributeModifier.Operation.ADDITION)
        }

        @JvmStatic
        public fun multiplyBase(id: String, amount: Double): MachineAttributeModifierImpl {
            return MachineAttributeModifierImpl(id, amount, MachineAttributeModifier.Operation.MULTIPLY_BASE)
        }

        @JvmStatic
        public fun multiplyTotal(id: String, amount: Double): MachineAttributeModifierImpl {
            return MachineAttributeModifierImpl(id, amount, MachineAttributeModifier.Operation.MULTIPLY_TOTAL)
        }
    }

}
