package github.kasuminova.prototypemachinery.client.impl.render.assets

import github.kasuminova.prototypemachinery.client.api.render.AssetResolver
import net.minecraft.client.resources.IResource
import net.minecraft.client.resources.IResourceManager
import net.minecraft.client.resources.data.IMetadataSection
import net.minecraft.util.ResourceLocation
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Adapter that lets code expecting vanilla [IResourceManager] read data via our [AssetResolver].
 *
 * This is mainly used to feed GeckoLib's loaders (`GeoModelLoader` / `AnimationFileLoader`) without
 * requiring Minecraft resource packs to be set up for dev external folders.
 */
internal class ResolverBackedResourceManager(
    private val resolver: AssetResolver,
    private val domains: Set<String> = emptySet(),
) : IResourceManager {

    override fun getResourceDomains(): Set<String> = domains

    @Throws(IOException::class)
    override fun getResource(location: ResourceLocation): IResource {
        if (!resolver.exists(location)) {
            throw FileNotFoundException(location.toString())
        }
        return ResolverBackedResource(location, resolver)
    }

    @Throws(IOException::class)
    override fun getAllResources(location: ResourceLocation): List<IResource> {
        // We only support a single resource per location.
        return listOf(getResource(location))
    }
}

internal class ResolverBackedResource(
    private val location: ResourceLocation,
    private val resolver: AssetResolver,
) : IResource {

    private var input: InputStream? = null

    override fun getResourceLocation(): ResourceLocation = location

    override fun getInputStream(): InputStream {
        val opened = resolver.open(location)
        input = opened
        return opened
    }

    override fun hasMetadata(): Boolean = false

    override fun <T : IMetadataSection?> getMetadata(sectionName: String): T? = null

    override fun getResourcePackName(): String = "PM-Resolver"

    override fun close() {
        try {
            input?.close()
        } finally {
            input = null
        }
    }
}
