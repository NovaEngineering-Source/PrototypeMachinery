package github.kasuminova.prototypemachinery.client.preview.scan

import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.util.math.BlockPos

/**
 * Client-side cache for world scan sessions.
 *
 * Goals:
 * - Allow multiple hosts / screens to reuse a scanner for the same (structureId, model, dim, anchor).
 * - Keep scanning optional: callers decide when to tick/use the scanner.
 * - Avoid leaking old worlds: cache entries are invalidated when the World instance changes.
 */
internal object StructurePreviewWorldScanCache {

    /**
     * A mostly-stable fingerprint for a [StructurePreviewModel].
     *
     * We intentionally avoid depending on object identity because most callers rebuild models when opening screens.
     */
    private fun fingerprint(model: StructurePreviewModel): Int {
        var h = 1
        // Entry order is not guaranteed; fold should be order-independent.
        // Use XOR of hashed entries to reduce sensitivity to iteration order.
        var x = 0
        for ((pos, req) in model.blocks) {
            var eh = 1
            eh = 31 * eh + pos.x
            eh = 31 * eh + pos.y
            eh = 31 * eh + pos.z
            eh = 31 * eh + req.stableKey().hashCode()
            x = x xor eh
        }
        h = 31 * h + x
        h = 31 * h + model.blocks.size
        h = 31 * h + model.bounds.min.x
        h = 31 * h + model.bounds.min.y
        h = 31 * h + model.bounds.min.z
        h = 31 * h + model.bounds.max.x
        h = 31 * h + model.bounds.max.y
        h = 31 * h + model.bounds.max.z
        return h
    }

    internal data class Key(
        val structureId: String,
        val dimension: Int,
        val anchor: BlockPos,
        val modelFingerprint: Int
    )

    private data class Entry(
        val world: WorldClient,
        val scanner: StructurePreviewWorldScanner,
        var lastAccessWorldTime: Long
    )

    private var cachedWorldIdentity: WorldClient? = null

    private val map: LinkedHashMap<Key, Entry> = LinkedHashMap()

    /** Hard limit to prevent unbounded client memory growth. */
    private const val MAX_ENTRIES: Int = 32

    /**
     * Get an existing scanner from cache or create a new one.
     *
     * The returned scanner is safe to tick/use only while [world] is the active client world.
     */
    fun getOrCreate(
        world: WorldClient,
        structureId: String,
        model: StructurePreviewModel,
        anchor: BlockPos
    ): StructurePreviewWorldScanner {
        // Invalidate cache when the world instance changes (dimension change / disconnect / reload).
        if (cachedWorldIdentity != null && cachedWorldIdentity !== world) {
            map.clear()
        }
        cachedWorldIdentity = world

        val time = world.totalWorldTime
        val key = Key(
            structureId = structureId,
            dimension = world.provider.dimension,
            anchor = anchor,
            modelFingerprint = fingerprint(model)
        )

        val existing = map[key]
        if (existing != null && existing.world === world) {
            existing.lastAccessWorldTime = time
            return existing.scanner
        }

        val scanner = StructurePreviewWorldScanner(world, model, anchor)
        map[key] = Entry(world = world, scanner = scanner, lastAccessWorldTime = time)

        evictIfNeeded(worldTime = time)
        return scanner
    }

    /** Drop all cached scan sessions. */
    fun clear() {
        map.clear()
        cachedWorldIdentity = null
    }

    private fun evictIfNeeded(worldTime: Long) {
        if (map.size <= MAX_ENTRIES) return

        // Remove least-recently accessed entries first.
        val victims = map.entries
            .sortedBy { it.value.lastAccessWorldTime }
            .take(map.size - MAX_ENTRIES)
            .map { it.key }

        for (k in victims) {
            map.remove(k)
        }

        // Opportunistic cleanup: if any entry hasn't been touched for a long time, drop it.
        // (This is a soft policy; it helps when players open lots of previews.)
        val expiry = worldTime - 20L * 60L * 10L // 10 minutes
        val expired = map.entries
            .filter { it.value.lastAccessWorldTime < expiry }
            .map { it.key }
        for (k in expired) {
            map.remove(k)
        }
    }
}
