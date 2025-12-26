package github.kasuminova.prototypemachinery.client.api.render

import net.minecraft.util.EnumFacing

/**
 * Minimal instance data required by the renderer.
 *
 * This intentionally does NOT expose machine/TE internals to keep the rendering system decoupled.
 */
public interface Renderable {
    /**
     * Stable identity used for caching and lifecycle tracking.
     *
     * Typically this is a TileEntity instance, but can be any object with stable identity.
     */
    public val ownerKey: Any

    /** Cache key for model/texture/animation content. */
    public val renderKey: RenderKey

    /** World-space position (block-aligned) to render at. */
    public val x: Double
    public val y: Double
    public val z: Double

    /** Horizontal facing for simple block rotation; backends may ignore. */
    public val facing: EnumFacing

    /** Combined light value, or -1 for fullbright/bloom-only buckets. */
    public val combinedLight: Int
}
