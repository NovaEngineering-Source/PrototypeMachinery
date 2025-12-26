package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.render

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoStructureBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.SliceRenderMode
import net.minecraft.util.ResourceLocation
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod
import java.util.Locale

/**
 * ZenScript-friendly builder for [GeckoModelBinding]/[GeckoStructureBinding].
 *
 * All locations are normal resource locations:
 * - geo: "namespace:geo/xxx.geo.json"
 * - texture: "namespace:textures/...png"
 * - animation: "namespace:animations/xxx.animation.json" (optional)
 */
@ZenClass("mods.prototypemachinery.render.GeckoBinding")
@ZenRegister
public class ZenGeckoBindingBuilder {

    private var geo: ResourceLocation? = null
    private var texture: ResourceLocation? = null
    private var animation: ResourceLocation? = null

    private var defaultAnimationName: String? = null
    private var yOffset: Double = 0.0
    private var modelOffsetX: Double = 0.0
    private var modelOffsetY: Double = 0.0
    private var modelOffsetZ: Double = 0.0
    private var forceGlobalRenderer: Boolean = false

    private var pass: RenderPass = RenderPass.DEFAULT
    private var sliceRenderMode: SliceRenderMode = SliceRenderMode.STRUCTURE_ONLY

    @ZenMethod
    public fun geo(geo: String): ZenGeckoBindingBuilder {
        this.geo = ResourceLocation(geo)
        return this
    }

    @ZenMethod
    public fun texture(texture: String): ZenGeckoBindingBuilder {
        this.texture = ResourceLocation(texture)
        return this
    }

    @ZenMethod
    public fun animation(animation: String): ZenGeckoBindingBuilder {
        this.animation = ResourceLocation(animation)
        return this
    }

    @ZenMethod
    public fun defaultAnimation(name: String): ZenGeckoBindingBuilder {
        this.defaultAnimationName = name
        return this
    }

    /** Extra vertical offset in blocks. */
    @ZenMethod
    public fun yOffset(y: Double): ZenGeckoBindingBuilder {
        this.yOffset = y
        return this
    }

    /**
     * Extra model offset in blocks (structure/model local units).
     *
     * This offset rotates with the structure orientation (front/top).
     * Note: for structure-bound rendering, the structure node's own offset is also applied.
     */
    @ZenMethod
    public fun modelOffset(x: Double, y: Double, z: Double): ZenGeckoBindingBuilder {
        this.modelOffsetX = x
        this.modelOffsetY = y
        this.modelOffsetZ = z
        return this
    }

    /** Disable frustum culling for very large models. */
    @ZenMethod
    public fun forceGlobalRenderer(enabled: Boolean): ZenGeckoBindingBuilder {
        this.forceGlobalRenderer = enabled
        return this
    }

    /**
     * Render pass selection. Supported (case-insensitive):
     * - "default"
     * - "transparent"
     * - "bloom"
     * - "bloom_transparent"
     */
    @ZenMethod
    public fun pass(pass: String): ZenGeckoBindingBuilder {
        val p = pass.trim().lowercase(Locale.ROOT)
        this.pass = when (p) {
            "transparent" -> RenderPass.TRANSPARENT
            "bloom" -> RenderPass.BLOOM
            "bloom_transparent", "bloomtransparent", "bloom-transparent" -> RenderPass.BLOOM_TRANSPARENT
            else -> RenderPass.DEFAULT
        }
        return this
    }

    /**
     * For Slice structures: how many anchors to create.
     * Supported (case-insensitive):
     * - "structure_only" (default)
     * - "per_slice"
     */
    @ZenMethod
    public fun sliceMode(mode: String): ZenGeckoBindingBuilder {
        val m = mode.trim().lowercase(Locale.ROOT)
        this.sliceRenderMode = when (m) {
            "per_slice", "perslice", "each" -> SliceRenderMode.PER_SLICE
            else -> SliceRenderMode.STRUCTURE_ONLY
        }
        return this
    }

    internal fun buildModelBinding(): GeckoModelBinding {
        val g = geo ?: throw IllegalStateException("GeckoBinding.geo(...) is required")
        val t = texture ?: throw IllegalStateException("GeckoBinding.texture(...) is required")

        return GeckoModelBinding(
            geo = g,
            texture = t,
            animation = animation,
            defaultAnimationName = defaultAnimationName,
            pass = pass,
            modelOffsetX = modelOffsetX,
            modelOffsetY = modelOffsetY,
            modelOffsetZ = modelOffsetZ,
            yOffset = yOffset,
            forceGlobalRenderer = forceGlobalRenderer,
        )
    }

    internal fun buildStructureBinding(): GeckoStructureBinding {
        return GeckoStructureBinding(
            model = buildModelBinding(),
            sliceRenderMode = sliceRenderMode,
        )
    }
}
