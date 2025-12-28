package github.kasuminova.prototypemachinery.client.impl.render.assets

import github.kasuminova.prototypemachinery.client.api.render.AssetResolver
import net.minecraft.util.ResourceLocation
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

/**
 * Dev/helper resolver that can read assets directly from a directory.
 *
 * Intended for testing GeckoLib assets from an instance folder like:
 * `<mcDir>/resources/<namespace>/<path>`
 *
 * Example:
 * - `modularmachinery:geo/dream_energy_core.geo.json`
 *   -> `<mcDir>/resources/modularmachinery/geo/dream_energy_core.geo.json`
 */
internal class MountedDirectoryAssetResolver(
    private val delegate: AssetResolver,
    private val rootDir: Path,
    private val namespaces: Set<String> = emptySet(),
) : AssetResolver {

    private val observedStamp = AtomicLong(0L)

    override fun open(location: ResourceLocation): InputStream {
        val mounted = toMountedPathOrNull(location)
        if (mounted != null && Files.isRegularFile(mounted)) {
            observe(mounted)
            return Files.newInputStream(mounted)
        }
        if (delegate.exists(location)) {
            return delegate.open(location)
        }
        throw FileNotFoundException(location.toString())
    }

    override fun exists(location: ResourceLocation): Boolean {
        val mounted = toMountedPathOrNull(location)
        if (mounted != null && Files.isRegularFile(mounted)) {
            return true
        }
        return delegate.exists(location)
    }

    override fun versionStamp(): Long {
        return maxOf(delegate.versionStamp(), observedStamp.get())
    }

    private fun toMountedPathOrNull(location: ResourceLocation): Path? {
        val ns = location.namespace
        if (namespaces.isNotEmpty() && ns !in namespaces) return null
        // Map namespace:path -> <root>/<namespace>/<path>
        return rootDir.resolve(ns).resolve(location.path)
    }

    private fun observe(file: Path) {
        try {
            val mtime = Files.getLastModifiedTime(file).toMillis()
            observedStamp.getAndUpdate { prev -> if (mtime > prev) mtime else prev }
        } catch (_: Exception) {
            // Best-effort only.
        }
    }
}
