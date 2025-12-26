package github.kasuminova.prototypemachinery.client.api.render.binding

/**
 * How a structure binding should be instantiated when the structure is a SliceStructure.
 *
 * - STRUCTURE_ONLY: render exactly once at the structure's base origin.
 * - PER_SLICE: render once per matched slice (at base + i * sliceOffset).
 */
public enum class SliceRenderMode {
    STRUCTURE_ONLY,
    PER_SLICE,
}
