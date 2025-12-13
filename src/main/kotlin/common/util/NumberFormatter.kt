package github.kasuminova.prototypemachinery.common.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

/**
 * # NumberFormatter - Number Formatting Utilities
 * # NumberFormatter - 数字格式化工具
 *
 * Provides utilities for formatting large numbers with SI prefixes
 * and other common number formatting operations.
 *
 * 提供使用 SI 前缀格式化大数字的工具
 * 以及其他常见的数字格式化操作。
 */
public object NumberFormatter {

    private val COMPACT_FORMAT = DecimalFormat("#.##")
    private val COMMAS_FORMAT: NumberFormat = NumberFormat.getNumberInstance(Locale.US)

    // SI prefixes for large numbers
    private val SI_PREFIXES = arrayOf(
        "" to 1L,
        "k" to 1_000L,
        "M" to 1_000_000L,
        "G" to 1_000_000_000L,
        "T" to 1_000_000_000_000L,
        "P" to 1_000_000_000_000_000L,
        "E" to 1_000_000_000_000_000_000L
    )

    // Energy-specific suffixes
    private val ENERGY_SUFFIXES = arrayOf(
        "RF" to 1L,
        "kRF" to 1_000L,
        "MRF" to 1_000_000L,
        "GRF" to 1_000_000_000L,
        "TRF" to 1_000_000_000_000L,
        "PRF" to 1_000_000_000_000_000L,
        "ERF" to 1_000_000_000_000_000_000L
    )

    // Fluid-specific suffixes (mB based)
    private val FLUID_SUFFIXES = arrayOf(
        "mB" to 1L,
        "B" to 1_000L,
        "kB" to 1_000_000L,
        "MB" to 1_000_000_000L,
        "GB" to 1_000_000_000_000L,
        "TB" to 1_000_000_000_000_000L
    )

    /**
     * Formats a number with SI prefix (compact form).
     * Example: 1500 -> "1.5k", 2500000 -> "2.5M"
     *
     * 使用 SI 前缀格式化数字（紧凑形式）。
     * 示例：1500 -> "1.5k"，2500000 -> "2.5M"
     */
    @JvmStatic
    public fun formatCompact(value: Long): String {
        if (value < 1000) return value.toString()

        for (i in SI_PREFIXES.indices.reversed()) {
            val (prefix, threshold) = SI_PREFIXES[i]
            if (value >= threshold) {
                val scaled = value.toDouble() / threshold
                return "${COMPACT_FORMAT.format(scaled)}$prefix"
            }
        }
        return value.toString()
    }

    /**
     * Formats a number with commas as thousand separators.
     * Example: 1234567 -> "1,234,567"
     *
     * 使用逗号作为千位分隔符格式化数字。
     * 示例：1234567 -> "1,234,567"
     */
    @JvmStatic
    public fun formatWithCommas(value: Long): String {
        return COMMAS_FORMAT.format(value)
    }

    /**
     * Formats energy with appropriate suffix.
     * Example: 1500 -> "1.5kRF", 2500000 -> "2.5MRF"
     *
     * 使用适当的后缀格式化能量。
     * 示例：1500 -> "1.5kRF"，2500000 -> "2.5MRF"
     */
    @JvmStatic
    public fun formatEnergy(value: Long): String {
        if (value < 1000) return "${value}RF"

        for (i in ENERGY_SUFFIXES.indices.reversed()) {
            val (suffix, threshold) = ENERGY_SUFFIXES[i]
            if (value >= threshold) {
                val scaled = value.toDouble() / threshold
                return "${COMPACT_FORMAT.format(scaled)}$suffix"
            }
        }
        return "${value}RF"
    }

    /**
     * Formats energy as a ratio (current / max).
     * Example: formatEnergyRatio(1500, 10000) -> "1.5kRF / 10kRF"
     *
     * 将能量格式化为比率（当前 / 最大）。
     * 示例：formatEnergyRatio(1500, 10000) -> "1.5kRF / 10kRF"
     */
    @JvmStatic
    public fun formatEnergyRatio(current: Long, max: Long): String {
        return "${formatEnergy(current)} / ${formatEnergy(max)}"
    }

    /**
     * Formats fluid amount with appropriate suffix.
     * Example: 1500 -> "1.5B", 2500000 -> "2.5kB"
     *
     * 使用适当的后缀格式化流体量。
     * 示例：1500 -> "1.5B"，2500000 -> "2.5kB"
     */
    @JvmStatic
    public fun formatFluid(value: Long): String {
        if (value < 1000) return "${value}mB"

        for (i in FLUID_SUFFIXES.indices.reversed()) {
            val (suffix, threshold) = FLUID_SUFFIXES[i]
            if (value >= threshold) {
                val scaled = value.toDouble() / threshold
                return "${COMPACT_FORMAT.format(scaled)}$suffix"
            }
        }
        return "${value}mB"
    }

    /**
     * Formats fluid as a ratio (current / max).
     * Example: formatFluidRatio(1500, 10000) -> "1.5B / 10B"
     *
     * 将流体格式化为比率（当前 / 最大）。
     * 示例：formatFluidRatio(1500, 10000) -> "1.5B / 10B"
     */
    @JvmStatic
    public fun formatFluidRatio(current: Long, max: Long): String {
        return "${formatFluid(current)} / ${formatFluid(max)}"
    }

    /**
     * Formats a transfer rate (per tick or per second).
     * Example: formatRate(100, "t") -> "100/t", formatRate(2000, "s") -> "2k/s"
     *
     * 格式化传输速率（每刻或每秒）。
     * 示例：formatRate(100, "t") -> "100/t"，formatRate(2000, "s") -> "2k/s"
     */
    @JvmStatic
    public fun formatRate(value: Long, unit: String): String {
        return "${formatCompact(value)}/$unit"
    }

    /**
     * Formats a percentage.
     * Example: formatPercentage(0.756) -> "75.6%"
     *
     * 格式化百分比。
     * 示例：formatPercentage(0.756) -> "75.6%"
     */
    @JvmStatic
    public fun formatPercentage(value: Double): String {
        return "${COMPACT_FORMAT.format(value * 100)}%"
    }

    /**
     * Formats a percentage from current/max values.
     * Example: formatPercentage(75, 100) -> "75%"
     *
     * 从当前/最大值格式化百分比。
     * 示例：formatPercentage(75, 100) -> "75%"
     */
    @JvmStatic
    public fun formatPercentage(current: Long, max: Long): String {
        if (max <= 0) return "0%"
        return formatPercentage(current.toDouble() / max)
    }

}
