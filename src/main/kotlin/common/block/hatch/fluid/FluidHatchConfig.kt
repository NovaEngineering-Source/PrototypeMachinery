package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType

/**
 * # FluidHatchConfig - Fluid Hatch Configuration
 * # FluidHatchConfig - 流体仓配置
 *
 * Configuration for fluid hatches defining tier, type, and capacity.
 *
 * 流体仓的配置，定义等级、类型和容量。
 *
 * @param tier The tier of the hatch
 * @param hatchType The type of the hatch (INPUT, OUTPUT, IO)
 * @param tankCount Number of fluid tanks
 * @param tankCapacity Capacity per tank in mB
 */
public data class FluidHatchConfig(
    val tier: HatchTier,
    val hatchType: HatchType,
    val tankCount: Int,
    val tankCapacity: Long
) {

    public companion object {

        /**
         * Tank counts by tier.
         * 按等级的储罐数量。
         * LV: 1, MV: 2, HV: 4, EV: 9, IV: 16, LuV: 16, ZPM: 16, UV: 25, UHV: 25, UEV: 25
         */
        private val TANK_COUNTS: IntArray = intArrayOf(
            1, 2, 4, 9, 16, 16, 16, 25, 25, 25
        )

        /**
         * Tank capacities by tier (in mB).
         * 按等级的储罐容量（mB）。
         * LV: 16000, MV: 32000, HV: 64000, EV: 128000, IV: 256000,
         * LuV: 512000, ZPM: 1024000, UV: 2048000, UHV: 4096000, UEV: 8192000
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
         * Creates a default configuration for the specified tier.
         * 为指定等级创建默认配置。
         */
        public fun createDefault(tierLevel: Int, hatchType: HatchType = HatchType.INPUT): FluidHatchConfig {
            val tier = HatchTier.fromTier(tierLevel)
            val index = (tierLevel - 1).coerceIn(0, TANK_COUNTS.size - 1)
            return FluidHatchConfig(
                tier = tier,
                hatchType = hatchType,
                tankCount = TANK_COUNTS[index],
                tankCapacity = TANK_CAPACITIES[index]
            )
        }

        /**
         * Creates an IO hatch configuration for the specified tier.
         * 为指定等级创建交互仓配置。
         */
        public fun createIO(tierLevel: Int): FluidHatchConfig {
            return createDefault(tierLevel, HatchType.IO)
        }

    }

}
