package github.kasuminova.prototypemachinery.client.impl.render.assets

import github.kasuminova.prototypemachinery.client.api.render.AssetResolver
import net.minecraft.util.ResourceLocation
import java.io.InputStream

/**
 * Resolver wrapper that will later decrypt encrypted-at-rest assets.
 *
 * For now:
 * - Delegates to an underlying resolver when unlocked.
 * - Refuses access to "secure" paths when locked.
 *
 * This design avoids triggering global resource reload: caches are invalidated via [versionStamp].
 */
internal class SecureAssetResolver(
    private val delegate: AssetResolver,
) : AssetResolver {

    public override fun open(location: ResourceLocation): InputStream {
        if (isSecure(location) && !SecureSession.unlocked) {
            throw IllegalStateException("Secure assets are locked (not in a server session): $location")
        }
        // TODO: decrypt here when isSecure(location)
        return delegate.open(location)
    }

    public override fun exists(location: ResourceLocation): Boolean {
        if (isSecure(location) && !SecureSession.unlocked) return false
        return delegate.exists(location)
    }

    public override fun versionStamp(): Long {
        // Include session stamp so render/model caches automatically invalidate on key rotation.
        return maxOf(delegate.versionStamp(), SecureSession.versionStamp())
    }

    private fun isSecure(location: ResourceLocation): Boolean {
        // Convention: namespace:path, we treat "secure/" subtree as encrypted-at-rest.
        return location.path.startsWith("secure/")
    }
}
