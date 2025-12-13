package github.kasuminova.prototypemachinery.common.block.hatch.item

import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType

/**
 * # ItemHatchConfig - Item Hatch Configuration
 * # ItemHatchConfig - 物品仓配置
 *
 * Configuration for item hatches including slot counts and stack limits.
 *
 * 物品仓的配置，包括槽位数量和堆叠限制。
 *
 * @param tier The tier configuration
 * @param hatchType The hatch type (INPUT/OUTPUT/IO)
 * @param slotCount Number of item slots
 * @param maxStackSize Maximum stack size per slot
 */
public data class ItemHatchConfig(
    val tier: HatchTier,
    val hatchType: HatchType,
    val slotCount: Int,
    val maxStackSize: Long
) {
    public companion object {
        /**
         * Default configurations for each tier.
         * 每个等级的默认配置。
         */
        public fun createDefault(tier: Int, hatchType: HatchType): ItemHatchConfig {
            val hatchTier = HatchTier.fromTier(tier)
            val (slots, maxStack) = when (tier) {
                1 -> 1 to 64L
                2 -> 2 to 64L
                3 -> 4 to 128L
                4 -> 9 to 256L
                5 -> 16 to 512L
                6 -> 25 to 1024L
                7 -> 36 to 2048L
                8 -> 49 to 4096L
                9 -> 64 to 8192L
                10 -> 81 to 16384L
                else -> 1 to 64L
            }
            return ItemHatchConfig(hatchTier, hatchType, slots, maxStack)
        }

        /**
         * Default input hatch configurations.
         * 默认输入仓配置。
         */
        public val INPUT_CONFIGS: List<ItemHatchConfig> = (1..10).map { createDefault(it, HatchType.INPUT) }

        /**
         * Default output hatch configurations.
         * 默认输出仓配置。
         */
        public val OUTPUT_CONFIGS: List<ItemHatchConfig> = (1..10).map { createDefault(it, HatchType.OUTPUT) }

        /**
         * Default IO hatch configurations.
         * 默认交互仓配置。
         */
        public val IO_CONFIGS: List<ItemHatchConfig> = (1..10).map { createDefault(it, HatchType.IO) }
    }
}

/**
 * # ItemIOHatchConfig - Item IO Hatch Configuration
 * # ItemIOHatchConfig - 物品交互仓配置
 *
 * Configuration for IO hatches with separate input and output sections.
 *
 * 具有独立输入和输出区域的交互仓配置。
 *
 * @param tier The tier configuration
 * @param inputSlotCount Number of input slots
 * @param outputSlotCount Number of output slots
 * @param inputMaxStackSize Maximum stack size per input slot
 * @param outputMaxStackSize Maximum stack size per output slot
 */
public data class ItemIOHatchConfig(
    val tier: HatchTier,
    val inputSlotCount: Int,
    val outputSlotCount: Int,
    val inputMaxStackSize: Long,
    val outputMaxStackSize: Long
) {
    public companion object {
        /**
         * Default configurations for each tier.
         * 每个等级的默认配置。
         */
        public fun createDefault(tier: Int): ItemIOHatchConfig {
            val hatchTier = HatchTier.fromTier(tier)
            val (inputSlots, outputSlots, inputMax, outputMax) = when (tier) {
                1 -> Quad(1, 1, 64L, 64L)
                2 -> Quad(1, 1, 64L, 64L)
                3 -> Quad(2, 2, 128L, 128L)
                4 -> Quad(4, 4, 256L, 256L)
                5 -> Quad(8, 8, 512L, 512L)
                6 -> Quad(12, 12, 1024L, 1024L)
                7 -> Quad(18, 18, 2048L, 2048L)
                8 -> Quad(24, 24, 4096L, 4096L)
                9 -> Quad(32, 32, 8192L, 8192L)
                10 -> Quad(40, 40, 16384L, 16384L)
                else -> Quad(1, 1, 64L, 64L)
            }
            return ItemIOHatchConfig(hatchTier, inputSlots, outputSlots, inputMax, outputMax)
        }

        /**
         * Default IO hatch configurations.
         * 默认交互仓配置。
         */
        public val CONFIGS: List<ItemIOHatchConfig> = (1..10).map { createDefault(it) }
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
