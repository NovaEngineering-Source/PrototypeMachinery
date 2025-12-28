package github.kasuminova.prototypemachinery.client.impl.render.assets

import github.kasuminova.prototypemachinery.client.api.render.AssetResolver
import net.minecraft.util.ResourceLocation
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory byte cache for [AssetResolver.open].
 *
 * Why:
 * - GeckoLib loaders (GeoModelLoader / AnimationFileLoader) may repeatedly open the same JSON resources
 *   across many build tasks (and many machine instances).
 * - When those resources live inside jars/zipfs or external folders, repeated open()+read() becomes a
 *   measurable I/O + decompression + allocation cost.
 *
 * This wrapper caches the fully-read bytes per [ResourceLocation] and invalidates the cache when
 * [delegate.versionStamp] changes.
 *
 * Note: the first open for a resource still performs I/O on the calling thread.
 * Our render pipeline should only call this from background build threads.
 */
internal class CachingAssetResolver(
    private val delegate: AssetResolver,
) : AssetResolver {

    private val cache = ConcurrentHashMap<ResourceLocation, ByteArray>()

    @Volatile
    private var lastStamp: Long = Long.MIN_VALUE

    override fun open(location: ResourceLocation): InputStream {
        val stamp = delegate.versionStamp()
        if (stamp != lastStamp) {
            // Best-effort invalidation. Races are fine: worst case we miss one clear.
            cache.clear()
            lastStamp = stamp
        }

        val bytes = cache.computeIfAbsent(location) {
            delegate.open(location).use { it.readBytes() }
        }

        return ByteArrayInputStream(bytes)
    }

    override fun exists(location: ResourceLocation): Boolean {
        return delegate.exists(location)
    }

    override fun versionStamp(): Long {
        return delegate.versionStamp()
    }
}
