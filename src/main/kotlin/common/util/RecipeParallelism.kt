package github.kasuminova.prototypemachinery.common.util

import github.kasuminova.prototypemachinery.api.machine.attribute.StandardMachineAttributes
import github.kasuminova.prototypemachinery.api.recipe.process.RecipeProcess
import kotlin.math.floor

/**
 * Utilities for recipe parallelism.
 *
 * IMPORTANT:
 * - We treat PROCESS_PARALLELISM on the process attribute map as the **effective** parallel amount
 *   for this specific process instance.
 * - The value is floored to an integer and clamped to >= 1.
 */
public object RecipeParallelism {

    public fun getParallelism(process: RecipeProcess): Int {
        val raw = process.attributeMap.attributes[StandardMachineAttributes.PROCESS_PARALLELISM]?.value ?: 1.0
        if (raw.isNaN() || raw.isInfinite()) return 1
        return floor(raw).toInt().coerceAtLeast(1)
    }

    /**
     * Scales a non-negative [base] by integer [k] with overflow protection.
     *
     * If overflow would occur, this returns [Long.MAX_VALUE] (saturating arithmetic).
     */
    public fun scaleCount(base: Long, k: Int): Long {
        if (base <= 0L) return base
        if (k <= 1) return base
        if (base > Long.MAX_VALUE / k.toLong()) return Long.MAX_VALUE
        return base * k.toLong()
    }
}

/** Shortcut extension: effective parallels for this process (>= 1). */
public fun RecipeProcess.parallelism(): Int = RecipeParallelism.getParallelism(this)

/** Shortcut extension: scale count by this process's parallelism. */
public fun RecipeProcess.scaleByParallelism(base: Long): Long = RecipeParallelism.scaleCount(base, parallelism())
