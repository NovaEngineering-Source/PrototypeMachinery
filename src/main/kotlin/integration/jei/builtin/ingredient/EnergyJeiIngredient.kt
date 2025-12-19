package github.kasuminova.prototypemachinery.integration.jei.builtin.ingredient

/**
 * Custom JEI ingredient representing energy IO.
 *
 * We intentionally keep [amount] positive and encode direction/timing as separate fields.
 */
public data class EnergyJeiIngredient(
    public val direction: Direction,
    public val timing: Timing,
    public val amount: Long,
) {

    public enum class Direction {
        CONSUME,
        PRODUCE,
    }

    public enum class Timing {
        ONCE,
        PER_TICK,
    }

    init {
        require(amount >= 0) { "amount must be >= 0" }
    }

    public companion object {
        @JvmStatic
        public fun consumeOnce(amount: Long): EnergyJeiIngredient = EnergyJeiIngredient(Direction.CONSUME, Timing.ONCE, amount)

        @JvmStatic
        public fun consumePerTick(amount: Long): EnergyJeiIngredient = EnergyJeiIngredient(Direction.CONSUME, Timing.PER_TICK, amount)

        @JvmStatic
        public fun produceOnce(amount: Long): EnergyJeiIngredient = EnergyJeiIngredient(Direction.PRODUCE, Timing.ONCE, amount)

        @JvmStatic
        public fun producePerTick(amount: Long): EnergyJeiIngredient = EnergyJeiIngredient(Direction.PRODUCE, Timing.PER_TICK, amount)
    }
}
