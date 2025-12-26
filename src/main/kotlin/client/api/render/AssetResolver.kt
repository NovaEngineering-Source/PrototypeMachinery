package github.kasuminova.prototypemachinery.client.api.render

import net.minecraft.util.ResourceLocation
import java.io.InputStream

/**
 * Abstraction over asset loading.
 *
 * This allows the rendering system to control loading timing and to support encrypted-at-rest assets.
 */
public interface AssetResolver {
    /**
     * Opens the resource as a stream.
     *
     * Implementations may decrypt on-the-fly.
     */
    public fun open(location: ResourceLocation): InputStream

    public fun exists(location: ResourceLocation): Boolean

    /**
     * Version stamp used for cache invalidation (e.g. when session key changes).
     */
    public fun versionStamp(): Long
}
