package github.kasuminova.prototypemachinery.client.impl.render.assets

import github.kasuminova.prototypemachinery.client.api.render.AssetResolver
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import java.io.InputStream

/**
 * Default resolver that loads assets from the current Minecraft resource manager.
 *
 * This is the baseline implementation; secure/encrypted resolvers can wrap or replace it.
 */
internal object MinecraftAssetResolver : AssetResolver {

    public override fun open(location: ResourceLocation): InputStream {
        return Minecraft.getMinecraft().resourceManager.getResource(location).inputStream
    }

    public override fun exists(location: ResourceLocation): Boolean {
        return try {
            Minecraft.getMinecraft().resourceManager.getResource(location)
            true
        } catch (_: Exception) {
            false
        }
    }

    public override fun versionStamp(): Long = 0L
}
