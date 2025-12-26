package github.kasuminova.prototypemachinery.client.api.render

import net.minecraft.util.ResourceLocation

/**
 * A stable key describing *what* will be rendered (not *where*).
 *
 * This is intended for caching background build results (buffers/VBOs).
 */
public data class RenderKey(
    val modelId: ResourceLocation,
    val textureId: ResourceLocation,
    val variant: Int = 0,
    /** Hash of animation state sampled at tick-rate (not per-frame). */
    val animationStateHash: Int = 0,
    /** Version stamp that invalidates cached decrypted assets. */
    val secureVersion: Long = 0L,
    /** Bit flags for backend-specific toggles (e.g. renderStaticOnly). */
    val flags: Int = 0,
    /**
     * Hash of orientation (front, top, twist) to invalidate cache when orientation changes.
     * Compute as: front.ordinal * 24 + top.ordinal * 4 + twist
     */
    val orientationHash: Int = 0,
)
