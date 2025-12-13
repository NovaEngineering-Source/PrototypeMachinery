package github.kasuminova.prototypemachinery.api.scheduler

import net.minecraft.util.math.BlockPos

/**
 * A recommended affinity key implementation for world-backed resources.
 *
 * - dimensionId + pos identifies a block entity position
 * - scope can distinguish multiple independent resources on the same position
 *   (e.g., "item_in", "item_out", "fluid_in", ...)
 */
public data class AffinityKey(
    public val dimensionId: Int,
    public val pos: BlockPos,
    public val scope: String = ""
) : Comparable<AffinityKey> {

    override fun compareTo(other: AffinityKey): Int {
        val dim = dimensionId.compareTo(other.dimensionId)
        if (dim != 0) return dim

        val x = pos.x.compareTo(other.pos.x)
        if (x != 0) return x

        val y = pos.y.compareTo(other.pos.y)
        if (y != 0) return y

        val z = pos.z.compareTo(other.pos.z)
        if (z != 0) return z

        return scope.compareTo(other.scope)
    }

}
