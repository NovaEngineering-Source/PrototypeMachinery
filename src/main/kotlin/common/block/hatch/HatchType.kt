package github.kasuminova.prototypemachinery.common.block.hatch

/**
 * # HatchType - Hatch Input/Output Type
 * # HatchType - 仓输入/输出类型
 *
 * Defines the operation mode of a hatch.
 *
 * 定义仓的操作模式。
 */
public enum class HatchType {
    /**
     * Input-only hatch.
     * 仅输入仓。
     */
    INPUT,

    /**
     * Output-only hatch.
     * 仅输出仓。
     */
    OUTPUT,

    /**
     * Input/Output (IO) hatch with separate input and output sections.
     * 输入/输出（IO）仓，具有独立的输入和输出区域。
     */
    IO
}

/**
 * # HatchTier - Hatch Tier Configuration
 * # HatchTier - 仓等级配置
 *
 * Defines the configuration for a specific tier of hatch.
 *
 * 定义特定等级仓的配置。
 *
 * @param tier The tier level (1-10)
 * @param name The display name suffix
 */
public data class HatchTier(
    val tier: Int,
    val name: String
) {
    init {
        require(tier in 1..10) { "Tier must be between 1 and 10" }
    }

    /**
     * Gets the GUI texture variant for this tier.
     * Tiers 1-8 share textures, 9 and 10 have unique textures.
     *
     * 获取此等级的 GUI 贴图变体。
     * 等级 1-8 共享贴图，9 和 10 有独特贴图。
     */
    public val guiVariant: String
        get() = when (tier) {
            in 1..8 -> "1_8"
            9 -> "9"
            10 -> "10"
            else -> "1_8"
        }

    public companion object {
        public val TIERS: List<HatchTier> = listOf(
            HatchTier(1, "Lv1"),
            HatchTier(2, "Lv2"),
            HatchTier(3, "Lv3"),
            HatchTier(4, "Lv4"),
            HatchTier(5, "Lv5"),
            HatchTier(6, "Lv6"),
            HatchTier(7, "Lv7"),
            HatchTier(8, "Lv8"),
            HatchTier(9, "Lv9"),
            HatchTier(10, "Lv10")
        )

        public fun fromTier(tier: Int): HatchTier = TIERS.getOrElse(tier - 1) { TIERS.first() }
    }
}
