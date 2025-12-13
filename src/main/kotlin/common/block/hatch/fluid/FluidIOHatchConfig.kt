package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier

/**
 * # FluidIOHatchConfig - Fluid IO Hatch Configuration
 * # FluidIOHatchConfig - 流体交互仓配置
 *
 * Configuration for fluid IO hatches with separate input and output tanks.
 *
 * 具有独立输入和输出储罐的流体交互仓配置。
 *
 * @param tier The tier of the hatch
 * @param inputTankCount Number of input tanks
 * @param outputTankCount Number of output tanks
 * @param inputTankCapacity Capacity per input tank in mB
 * @param outputTankCapacity Capacity per output tank in mB
 */
public data class FluidIOHatchConfig(
    val tier: HatchTier,
    val inputTankCount: Int,
    val outputTankCount: Int,
    val inputTankCapacity: Long,
    val outputTankCapacity: Long
) {

    public companion object {

        /**
         * Tank counts by tier for IO hatch (split between input/output).
         * 交互仓按等级的储罐数量（分配给输入/输出）。
         */
        private val IO_TANK_COUNTS: IntArray = intArrayOf(
            1, 1, 2, 4, 4, 4, 4, 6, 6, 6
        )

        /**
         * Tank capacities by tier (in mB).
         * 按等级的储罐容量（mB）。
         */
        private val TANK_CAPACITIES: LongArray = longArrayOf(
            16_000L,     // LV
            32_000L,     // MV
            64_000L,     // HV
            128_000L,    // EV
            256_000L,    // IV
            512_000L,    // LuV
            1_024_000L,  // ZPM
            2_048_000L,  // UV
            4_096_000L,  // UHV
            8_192_000L   // UEV
        )

        /**
         * Creates a default IO configuration for the specified tier.
         * 为指定等级创建默认交互配置。
         */
        public fun createDefault(tierLevel: Int): FluidIOHatchConfig {
            val tier = HatchTier.fromTier(tierLevel)
            val index = (tierLevel - 1).coerceIn(0, IO_TANK_COUNTS.size - 1)
            val tankCount = IO_TANK_COUNTS[index]
            return FluidIOHatchConfig(
                tier = tier,
                inputTankCount = tankCount,
                outputTankCount = tankCount,
                inputTankCapacity = TANK_CAPACITIES[index],
                outputTankCapacity = TANK_CAPACITIES[index]
            )
        }

    }

}
