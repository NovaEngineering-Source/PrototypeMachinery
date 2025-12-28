package github.kasuminova.prototypemachinery.client.impl.render.gecko

import net.minecraft.client.resources.IResourceManager
import net.minecraft.util.ResourceLocation
import software.bernie.geckolib3.file.GeoModelLoader
import software.bernie.geckolib3.geo.render.built.GeoModel
import java.util.Collections
import java.util.WeakHashMap

/**
 * Per-owner GeoModel cache.
 *
 * Why:
 * - Our dynamic (tick-rate) build tasks were re-loading/parsing the same .geo.json every tick.
 * - GeoModelLoader.loadModel() does I/O (open stream) + JSON parse + model construction, which is
 *   expensive and shows up as "IO" in profilers (IOUtils/zip inflater) even when the data comes from jars.
 *
 * Design:
 * - Cache is keyed by ownerKey (same semantics as RenderTaskCache / GeckoAnimationDriver runtimes),
 *   so each machine instance gets its own GeoModel object (bones are mutable!).
 * - Entries are invalidated when [stamp] changes (typically mounted directory mtime or session key).
 * - Keys are weak to avoid memory leaks when TEs unload.
 */
internal object GeckoGeoModelInstanceCache {

    private data class Entry(
        val geoLocation: ResourceLocation,
        val stamp: Long,
        val model: GeoModel,
    )

    private val cache: MutableMap<Any, Entry> = Collections.synchronizedMap(WeakHashMap())

    fun size(): Int = cache.size

    fun getOrLoad(ownerKey: Any, geoLocation: ResourceLocation, stamp: Long, resourceManager: IResourceManager): GeoModel {
        val existing = cache[ownerKey]
        if (existing != null && existing.geoLocation == geoLocation && existing.stamp == stamp) {
            return existing.model
        }

        val loaded = GeoModelLoader().loadModel(resourceManager, geoLocation)
        cache[ownerKey] = Entry(geoLocation = geoLocation, stamp = stamp, model = loaded)
        return loaded
    }

    fun clear(ownerKey: Any) {
        cache.remove(ownerKey)
    }

    fun clearAll() {
        cache.clear()
    }
}
