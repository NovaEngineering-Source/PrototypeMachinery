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

    /**
     * Optional list of animation names to be played concurrently (layered).
     *
     * - If non-empty, this takes precedence over [defaultAnimationName].
     * - Order matters: later layers overwrite conflicting bone transforms.
     */
    public val animationLayers: List<String> = emptyList(),

    /**
     * Optional ZSDataComponent key to override the effective animation name at runtime.
     *
     * This key is read on the client from the machine's synchronized ZSDataComponent,
     * enabling server-authoritative animation selection.
     *
     * Value format: a single string, e.g. "open".
     */
    public val animationStateKey: String? = null,

    /**
     * Optional ZSDataComponent key to override the effective animation layer list at runtime.
     *
     * Value format: comma-separated list, e.g. "base_idle,overlay_glow".
     */
    public val animationLayersStateKey: String? = null,

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
