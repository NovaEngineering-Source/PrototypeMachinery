package github.kasuminova.prototypemachinery.api.machine.attribute

public interface MachineAttributeModifier {

    public val id: String

    public val amount: Double

    public fun apply(base: Double, current: Double): Double

    public enum class Operation {
        ADDITION,
        MULTIPLY_BASE,
        MULTIPLY_TOTAL,
    }

}