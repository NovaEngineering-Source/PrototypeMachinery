package github.kasuminova.prototypemachinery.client.atlas

import com.cleanroommc.modularui.drawable.UITexture
import net.minecraft.util.ResourceLocation

/**
 * Public (to client module) texture access for GUI sprites.
 *
 * Call sites should use this instead of hard-coded UVs.
 */
internal object PmGuiTextures {

    fun get(path: String): UITexture = get(ResourceLocation(path))

    fun get(rl: ResourceLocation): UITexture {
        return PmGuiAtlas.getSpriteTexture(rl)
    }
}
