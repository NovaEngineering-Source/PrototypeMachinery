package github.kasuminova.prototypemachinery.api.platform;

/**
 * Optional high-performance vertex pipeline for Gecko baking.
 *
 * <p>This interface lives in the Java 8 compatible main jar.
 * A separate backend mod (e.g. modern-backend on Java 21+) can provide an implementation
 * via {@link PMPlatform#geckoVertexPipeline()}.
 *
 * <p>Design notes:
 * <ul>
 *   <li>No reference to Vector API or modern-backend classes.</li>
 *   <li>Call sites are hot; implementations should be allocation-free and ideally cache any state.</li>
 *   <li>Returning {@code false} indicates the caller should fall back to the legacy path.</li>
 * </ul>
 */
public interface PMGeckoVertexPipeline {

    /** Human readable backend name (selected vs scalar depends on {@code forceScalar}). */
    String backendName(boolean forceScalar);

    /** Whether the backend is vectorized (selected vs scalar depends on {@code forceScalar}). */
    boolean isVectorized(boolean forceScalar);

    /**
     * Transform SoA positions by an affine 3x4 matrix and pack to AoS int[] layout:
     * xBits, yBits, zBits, uBits, vBits, color, normal.
     */
    boolean transformThenPackIntArray(
            boolean forceScalar,
            float[] xs,
            float[] ys,
            float[] zs,
            float[] us,
            float[] vs,
            int[] colors,
            int[] normals,
            int vertexOffset,
            int count,
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23,
            int[] out,
            int outIntOffset
    );

    /**
     * Optional: transform SoA positions in-place.
     *
     * <p>This enables call sites to pack their own AoS output (e.g. when color/normal are
     * constant per-quad and should not be materialized into per-vertex arrays).
     *
     * @return true if supported and executed; false if not supported.
     */
    default boolean transformAffine3x4InPlace(
            boolean forceScalar,
            float[] xs,
            float[] ys,
            float[] zs,
            int vertexOffset,
            int count,
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23
    ) {
        return false;
    }
}
