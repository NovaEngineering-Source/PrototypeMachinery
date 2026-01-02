@file:Suppress("DEPRECATION")

package github.kasuminova.prototypemachinery.client.atlas

import com.cleanroommc.modularui.drawable.UITexture
import github.kasuminova.prototypemachinery.PrototypeMachinery
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.ITextureMapPopulator
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.resources.IReloadableResourceManager
import net.minecraft.client.resources.IResourceManager
import net.minecraft.client.resources.IResourceManagerReloadListener
import net.minecraft.util.ResourceLocation
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentHashMap

/**
 * Runtime GUI atlas based on Minecraft 1.12 TextureMap (uses Stitcher internally).
 *
 * - The atlas content is enumerated from an index json generated at build-time.
 * - Widgets still reference sprites by ResourceLocation-like paths.
 * - Provides ModularUI-compatible [UITexture] mapped to stitched UVs.
 */
internal object PmGuiAtlas : IResourceManagerReloadListener {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    /**
     * Registered as a texture object under TextureManager.
     * Must look like a `textures/<path>.png` location for ModularUI.
     */
    private val atlasLocation = ResourceLocation("prototypemachinery", "textures/gui/pm_gui_atlas.png")

    /** Build-time generated index location (assets/<modid>/pm_gui_atlas/<atlasId>.json). */
    private val atlasIndex = ResourceLocation("prototypemachinery", "pm_gui_atlas/gui_preview.json")

    @Volatile
    private var textureMap: TextureMap? = null

    private val textureCache = ConcurrentHashMap<ResourceLocation, UITexture>()

    fun init() {
        val mc = Minecraft.getMinecraft()
        val rm = mc.resourceManager
        if (rm is IReloadableResourceManager) {
            rm.registerReloadListener(this)
        }
        // Build once at startup.
        onResourceManagerReload(rm)
    }

    fun getSpriteTexture(sprite: ResourceLocation): UITexture {
        return textureCache.computeIfAbsent(sprite) { key ->
            val tm = textureMap
            if (tm == null) {
                // Fallback to direct file access (non-atlased). Useful during early init.
                return@computeIfAbsent UITexture.fullImage(key)
            }

            val spr: TextureAtlasSprite = tm.getAtlasSprite(key.toString())
            UITexture(atlasLocation, spr.minU, spr.minV, spr.maxU, spr.maxV, null)
        }
    }

    override fun onResourceManagerReload(resourceManager: IResourceManager) {
        textureCache.clear()

        val sprites = loadIndexSprites(resourceManager)

        // Create a fresh texture map each reload; this avoids dealing with internal sprite state.
        val populator = ITextureMapPopulator { tm ->
            for (sprite in sprites) {
                tm.registerSprite(sprite)
            }
        }

        val tm = TextureMap("textures", populator)
        tm.setMipmapLevels(0)

        val mc = Minecraft.getMinecraft()
        mc.textureManager.loadTexture(atlasLocation, tm)

        textureMap = tm

        PrototypeMachinery.logger.info("[PmGuiAtlas] Reloaded GUI atlas: {} sprites", sprites.size)
    }

    private fun loadIndexSprites(resourceManager: IResourceManager): List<ResourceLocation> {
        return try {
            resourceManager.getResource(atlasIndex).use { res ->
                val def = InputStreamReader(res.inputStream, Charsets.UTF_8).use { r ->
                    json.decodeFromString(AtlasIndex.serializer(), r.readText())
                }
                def.sprites.map { ResourceLocation(it) }
            }
        } catch (t: Throwable) {
            PrototypeMachinery.logger.warn("[PmGuiAtlas] Failed to load atlas index {}, atlas will be empty.", atlasIndex, t)
            emptyList()
        }
    }

    @Serializable
    private data class AtlasIndex(
        val atlasId: String,
        val sprites: List<String>
    )
}
