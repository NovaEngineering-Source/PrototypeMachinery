package github.kasuminova.prototypemachinery.client.impl.render.assets

import github.kasuminova.prototypemachinery.PrototypeMachinery
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.texture.TextureUtil
import net.minecraft.util.ResourceLocation
import java.nio.file.Files
import java.nio.file.Path

/**
 * Binds textures with a fallback loader that reads from the local game directory.
 *
 * Why: our Gecko/asset pipeline can mount resources under `<gameDir>/resources/<namespace>/...`,
 * but vanilla TextureManager only looks inside resource packs (assets/...) and will warn+fail.
 *
 * This helper preloads/registers a [DynamicTexture] for the given [ResourceLocation] when a
 * matching PNG exists on disk.
 */
internal object ExternalDiskTextureBinder {

    private val lastModifiedMillis = Object2LongOpenHashMap<ResourceLocation>().apply {
        defaultReturnValue(Long.MIN_VALUE)
    }

    private val dynamicTextures = Object2ObjectOpenHashMap<ResourceLocation, DynamicTexture>()

    fun bind(texture: ResourceLocation) {
        ensureLoaded(texture)
        Minecraft.getMinecraft().renderEngine.bindTexture(texture)
    }

    private fun ensureLoaded(texture: ResourceLocation) {
        val path = resolveDiskPath(texture)
        if (!Files.isRegularFile(path)) return

        val mtime = runCatching { Files.getLastModifiedTime(path).toMillis() }.getOrElse { return }
        if (lastModifiedMillis.getLong(texture) == mtime) return

        try {
            Files.newInputStream(path).use { input ->
                val image = TextureUtil.readBufferedImage(input)
                val previous = dynamicTextures.put(texture, DynamicTexture(image))
                previous?.deleteGlTexture()
                Minecraft.getMinecraft().renderEngine.loadTexture(texture, dynamicTextures.getValue(texture))
                lastModifiedMillis.put(texture, mtime)
            }
        } catch (t: Throwable) {
            // Avoid spamming logs every frame. We only attempt reload when mtime changes.
            PrototypeMachinery.logger.warn("Failed to load external texture from disk: $texture ($path)", t)
        }
    }

    private fun resolveDiskPath(texture: ResourceLocation): Path {
        // Mirrors MountedDirectoryAssetResolver mapping: <gameDir>/resources/<namespace>/<path>
        val mcDir = Minecraft.getMinecraft().gameDir.toPath()
        return mcDir
            .resolve("resources")
            .resolve(texture.namespace)
            .resolve(texture.path)
    }
}
