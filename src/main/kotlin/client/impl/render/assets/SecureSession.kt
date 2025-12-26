package github.kasuminova.prototypemachinery.client.impl.render.assets

import java.util.concurrent.atomic.AtomicLong

/**
 * Client-side secure session state.
 *
 * This is intentionally minimal for now: it tracks a version stamp that invalidates caches
 * when the session transitions from locked -> unlocked (or when the key rotates).
 */
internal object SecureSession {

    private val version = AtomicLong(0L)

    @Volatile
    internal var unlocked: Boolean = false
        private set

    /** Version stamp used by [github.kasuminova.prototypemachinery.client.api.render.AssetResolver.versionStamp]. */
    internal fun versionStamp(): Long = version.get()

    internal fun lock() {
        unlocked = false
        version.incrementAndGet()
    }

    /**
     * Unlocks secure assets for the current server session.
     *
     * The actual key material / crypto will be implemented later; for now this only bumps the version.
     */
    internal fun unlock(/* keyMaterial: ByteArray */) {
        unlocked = true
        version.incrementAndGet()
    }
}
