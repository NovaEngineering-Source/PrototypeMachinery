package github.kasuminova.prototypemachinery.client.api.render.binding

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import net.minecraft.util.ResourceLocation

/**
 * Minimal declarative binding for a GeckoLib model.
 *
 * All locations are normal MC resource locations:
 * - geo: `namespace:geo/xxx.geo.json`
 * - animation: `namespace:animations/xxx.animation.json`
 * - texture: `namespace:textures/...png`
 */
public data class GeckoModelBinding(
    public val geo: ResourceLocation,
    public val texture: ResourceLocation,
    public val animation: ResourceLocation? = null,

    /** Optional default animation name inside the animation file. */
    public val defaultAnimationName: String? = null,

    /** Render pass selection (future: bloom / translucent routing). */
    public val pass: RenderPass = RenderPass.DEFAULT,

    /**
        * Extra model offset in blocks (structure/model local units).
     *
        * This is applied in addition to the base anchor position, and it rotates with the structure orientation.
        * For structure-bound rendering, the structure node's offset is also included automatically.
     */
    public val modelOffsetX: Double = 0.0,
    public val modelOffsetY: Double = 0.0,
    public val modelOffsetZ: Double = 0.0,

    /** Extra vertical offset in blocks. */
    public val yOffset: Double = 0.0,

    /**
     * If true, disables frustum culling for the machine TESR (still distance-culled).
     * Use this only for very large models where even an inflated render bounding box is not enough.
     */
    public val forceGlobalRenderer: Boolean = false,
)
