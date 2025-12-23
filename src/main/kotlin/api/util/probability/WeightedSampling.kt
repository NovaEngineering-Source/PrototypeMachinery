package github.kasuminova.prototypemachinery.api.util.probability

import java.util.Random

/**
 * Weighted sampling helpers.
 */
public object WeightedSampling {

    /**
     * Weighted item wrapper.
     */
    public data class Weighted<T>(
        public val value: T,
        public val weight: Int,
    )

    /**
     * Samples up to [count] unique elements from [candidates] (without replacement),
     * using their [Weighted.weight] as weights.
     *
     * Candidates with weight <= 0 are ignored.
     */
    public fun <T> sampleWithoutReplacement(
        random: Random,
        candidates: List<Weighted<T>>,
        count: Int,
    ): List<T> {
        if (count <= 0) return emptyList()

        val pool = candidates.filter { it.weight > 0 }.toMutableList()
        if (pool.isEmpty()) return emptyList()

        val out = ArrayList<T>(minOf(count, pool.size))
        repeat(minOf(count, pool.size)) {
            var total = 0L
            for (c in pool) total += c.weight.toLong()
            if (total <= 0L) return@repeat

            var r = (random.nextDouble() * total).toLong().coerceIn(0L, total - 1)
            var idx = -1
            for (i in pool.indices) {
                r -= pool[i].weight.toLong()
                if (r < 0L) {
                    idx = i
                    break
                }
            }

            if (idx < 0) idx = pool.lastIndex
            out.add(pool.removeAt(idx).value)
        }

        return out
    }
}
