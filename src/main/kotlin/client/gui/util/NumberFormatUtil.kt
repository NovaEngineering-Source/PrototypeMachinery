package github.kasuminova.prototypemachinery.client.gui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

/**
 * GUI number formatting utilities.
 *
 * GUI 数字格式化工具。
 *
 * Notes:
 * - Thousand separator is forced to comma via Locale.US to match in-game UI expectations.
 * - Tooltip prefers full values with grouping.
 * - Labels can use a compact format to avoid overflowing small text boxes.
 *
 * 备注：
 * - 千分位分隔符强制使用 Locale.US（逗号），以匹配游戏内 UI 的预期表现。
 * - Tooltip 优先展示带千分位的完整数值。
 * - Label 可使用紧凑格式（k/M/G）避免在较窄的文本区域溢出。
 */
public object NumberFormatUtil {

    private val intGroupedFormat: ThreadLocal<NumberFormat> = ThreadLocal.withInitial {
        NumberFormat.getIntegerInstance(Locale.US).apply {
            isGroupingUsed = true
            maximumFractionDigits = 0
        }
    }

    private val oneDecimalFormat: ThreadLocal<DecimalFormat> = ThreadLocal.withInitial {
        DecimalFormat("0.0", DecimalFormatSymbols(Locale.US)).apply {
            isGroupingUsed = false
        }
    }

    public fun formatGrouped(value: Long): String {
        return intGroupedFormat.get().format(value)
    }

    /**
     * Compact format for labels: 1_234 -> 1.2k, 12_345_678 -> 12.3M, etc.
     * Values below 1000 are still grouped (though grouping does nothing there).
     *
     * Label 用的紧凑格式：1_234 -> 1.2k、12_345_678 -> 12.3M 等。
     * 小于 1000 的数值仍会走 grouped 格式（但此时千分位不会产生变化）。
     */
    public fun formatCompact(value: Long): String {
        val v = value.coerceAtLeast(0L)
        return when {
            v >= 1_000_000_000L -> oneDecimalFormat.get().format(v / 1_000_000_000.0) + "G"
            v >= 1_000_000L -> oneDecimalFormat.get().format(v / 1_000_000.0) + "M"
            v >= 1_000L -> oneDecimalFormat.get().format(v / 1_000.0) + "k"
            else -> formatGrouped(v)
        }
    }

    public fun formatMbGrouped(value: Long): String = formatGrouped(value.coerceAtLeast(0L)) + "mB"

    public fun formatMbCompact(value: Long): String = formatCompact(value.coerceAtLeast(0L)) + "mB"

    public fun formatMbPairGrouped(amount: Long, capacity: Long): String {
        val a = amount.coerceAtLeast(0L)
        val c = capacity.coerceAtLeast(0L)
        return formatGrouped(a.coerceAtMost(c)) + " / " + formatGrouped(c) + "mB"
    }

    public fun formatMbPairCompact(amount: Long, capacity: Long): String {
        val a = amount.coerceAtLeast(0L)
        val c = capacity.coerceAtLeast(0L)
        return formatCompact(a.coerceAtMost(c)) + " / " + formatCompact(c) + "mB"
    }
}
