package github.kasuminova.prototypemachinery.common.block.hatch.energy

import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType

/**
 * # EnergyHatchConfig - Energy Hatch Configuration
 * # EnergyHatchConfig - 能量仓配置
 *
 * Configuration for energy hatches defining tier, type, and capacity.
 *
 * 能量仓的配置，定义等级、类型和容量。
 *
 * @param tier The tier of the hatch
 * @param hatchType The type of the hatch (INPUT, OUTPUT, IO)
 * @param capacity Energy storage capacity (in FE)
 * @param maxTransfer Maximum transfer rate per tick (in FE/t)
 */
public data class EnergyHatchConfig(
    val tier: HatchTier,
    val hatchType: HatchType,
    val capacity: Long,
    val maxTransfer: Long
) {

    public companion object {

        /**
         * Energy capacities by tier (in FE).
         * 按等级的能量容量（FE）。
         * LV: 128k, MV: 512k, HV: 2M, EV: 8M, IV: 32M,
         * LuV: 128M, ZPM: 512M, UV: 2G, UHV: 8G, UEV: 32G
         */
        private val CAPACITIES: LongArray = longArrayOf(
            128_000L,           // LV
            512_000L,           // MV
            2_000_000L,         // HV
            8_000_000L,         // EV
            32_000_000L,        // IV
            128_000_000L,       // LuV
            512_000_000L,       // ZPM
            2_000_000_000L,     // UV
            8_000_000_000L,     // UHV
            32_000_000_000L     // UEV
        )

        /**
         * Maximum transfer rates by tier (in FE/t).
         * 按等级的最大传输速率（FE/t）。
         * LV: 32, MV: 128, HV: 512, EV: 2048, IV: 8192,
         * LuV: 32768, ZPM: 131072, UV: 524288, UHV: 2097152, UEV: 8388608
         */
        private val MAX_TRANSFERS: LongArray = longArrayOf(
            32L,                // LV
            128L,               // MV
            512L,               // HV
            2_048L,             // EV
            8_192L,             // IV
            32_768L,            // LuV
            131_072L,           // ZPM
            524_288L,           // UV
            2_097_152L,         // UHV
            8_388_608L          // UEV
        )

        /**
         * Creates a default configuration for the specified tier.
         * 为指定等级创建默认配置。
         */
        public fun createDefault(tierLevel: Int, hatchType: HatchType = HatchType.INPUT): EnergyHatchConfig {
            val tier = HatchTier.fromTier(tierLevel)
            val index = (tierLevel - 1).coerceIn(0, CAPACITIES.size - 1)
            return EnergyHatchConfig(
                tier = tier,
                hatchType = hatchType,
                capacity = CAPACITIES[index],
                maxTransfer = MAX_TRANSFERS[index]
            )
        }

        /**
         * Creates an input configuration for the specified tier.
         * 为指定等级创建输入配置。
         */
        public fun createInput(tierLevel: Int): EnergyHatchConfig {
            return createDefault(tierLevel, HatchType.INPUT)
        }

        /**
         * Creates an output configuration for the specified tier.
         * 为指定等级创建输出配置。
         */
        public fun createOutput(tierLevel: Int): EnergyHatchConfig {
            return createDefault(tierLevel, HatchType.OUTPUT)
        }

        /**
         * Creates an IO configuration for the specified tier.
         * 为指定等级创建交互配置。
         */
        public fun createIO(tierLevel: Int): EnergyHatchConfig {
            return createDefault(tierLevel, HatchType.IO)
        }

    }

}
