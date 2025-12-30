package github.kasuminova.prototypemachinery.client.impl.render.task

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import net.minecraft.util.ResourceLocation
import java.util.IdentityHashMap

/**
 * Interning factory for RenderTaskCache owner keys.
 *
 * Why:
 * - If callers create a new data class key every frame, HashMap.get() will call equals() for every hit
 *   (because reference equality fails), which becomes a measurable render-thread hot spot.
 * - This interner returns the *same key instance* for the same (te, binding slot, part), so HashMap
 *   can usually short-circuit on reference equality and skip equals() entirely.
 */
internal object RenderTaskOwnerKeys {

    /**
     * Owner key used by RenderTaskCache. Equality is identity-only.
     *
     * We intentionally do not implement structural equals() because:
     * - We guarantee canonical instances via interning.
     * - Identity-only equals avoids expensive field comparisons in collision chains.
     */
    internal class OwnerKey internal constructor(
        internal val te: Any,
        private val cachedHash: Int,
    ) {
        override fun equals(other: Any?): Boolean = this === other
        override fun hashCode(): Int = cachedHash
    }

    private class TeStore {
        // For legacy bindings keyed by a stable bindingKey object (ResourceLocation etc.)
        val legacyByBinding: IdentityHashMap<Any, Array<OwnerKey?>> = IdentityHashMap()

        // For structure bindings keyed by (structureId, sliceIndex)
        val structureById: HashMap<String, Int2ObjectOpenHashMap<Array<OwnerKey?>>> = HashMap()

        fun collectAllKeys(): ArrayList<OwnerKey> {
            val out = ArrayList<OwnerKey>()

            for (arr in legacyByBinding.values) {
                for (k in arr) if (k != null) out.add(k)
            }

            for (m in structureById.values) {
                val it = m.values.iterator()
                while (it.hasNext()) {
                    val arr = it.next()
                    for (k in arr) if (k != null) out.add(k)
                }
            }

            return out
        }
    }

    private val perTe: IdentityHashMap<Any, TeStore> = IdentityHashMap()

    /**
     * Returns a canonical owner key for (te, bindingKey, partOrdinal).
     *
     * bindingKey must be a *stable* object identity for the slot (e.g. ResourceLocation constants).
     */
    internal fun legacyOwnerKey(te: Any, bindingKey: Any, partOrdinal: Int, partCount: Int): OwnerKey {
        val store = perTe.getOrPut(te) { TeStore() }
        val arr = store.legacyByBinding.getOrPut(bindingKey) { arrayOfNulls(partCount) }

        val existing = arr[partOrdinal]
        if (existing != null) return existing

        val h = mixHash(System.identityHashCode(te), System.identityHashCode(bindingKey), partOrdinal)
        val created = OwnerKey(te = te, cachedHash = h)
        arr[partOrdinal] = created
        return created
    }

    /**
     * Returns a canonical owner key for structure slot (te, machineTypeId, structureId, sliceIndex, partOrdinal).
     */
    internal fun structureOwnerKey(
        te: Any,
        machineTypeId: ResourceLocation,
        structureId: String,
        sliceIndex: Int,
        partOrdinal: Int,
        partCount: Int,
    ): OwnerKey {
        val store = perTe.getOrPut(te) { TeStore() }
        val bySlice = store.structureById.getOrPut(structureId) { Int2ObjectOpenHashMap() }
        val arr = bySlice.computeIfAbsent(sliceIndex) { arrayOfNulls(partCount) }

        val existing = arr[partOrdinal]
        if (existing != null) return existing

        // Using stable semantic hashes here is fine; identity-only equals avoids any deep comparisons.
        var h = System.identityHashCode(te)
        h = 31 * h + machineTypeId.hashCode()
        h = 31 * h + structureId.hashCode()
        h = 31 * h + sliceIndex
        h = 31 * h + partOrdinal
        val created = OwnerKey(te = te, cachedHash = h)
        arr[partOrdinal] = created
        return created
    }

    /**
     * Remove and return all interned keys belonging to [te].
     *
     * Called from RenderTaskCache.removeByTe to avoid leaks.
     */
    internal fun removeByTe(te: Any): List<OwnerKey> {
        val store = perTe.remove(te) ?: return emptyList()
        return store.collectAllKeys()
    }

    private fun mixHash(a: Int, b: Int, c: Int): Int {
        var h = a
        h = 31 * h + b
        h = 31 * h + c
        return h
    }
}
