package github.kasuminova.prototypemachinery.client.api.render

/**
 * Render pass/category used by the batching renderer.
 *
 * Passes may correspond to distinct GL state sets (e.g. depth mask, fullbright).
 */
public enum class RenderPass {
    DEFAULT,
    TRANSPARENT,
    BLOOM,
    BLOOM_TRANSPARENT,
}
