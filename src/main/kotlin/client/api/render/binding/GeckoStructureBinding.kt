package github.kasuminova.prototypemachinery.client.api.render.binding

/**
 * Binding of a Gecko model to a specific structure node within a machine.
 */
public data class GeckoStructureBinding(
    public val model: GeckoModelBinding,

    /** Behavior for SliceStructure anchoring. Ignored for non-slice structures. */
    public val sliceRenderMode: SliceRenderMode = SliceRenderMode.PER_SLICE,
)
