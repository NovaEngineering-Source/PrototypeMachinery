package github.kasuminova.prototypemachinery.modernbackend.accel;

/**
 * A tiny, hot-path friendly backend for bulk affine transforms.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Call site stays in the base jar (no Vector API references).</li>
 *   <li>Optional vectorized implementation may live in a separate jar.</li>
 *   <li>Batch-oriented API: Vector API benefits require processing many vertices per call.</li>
 * </ul>
 */
public interface PositionTransformBackend {

    /** Human readable name for diagnostics. */
    String name();

    /**
     * Returns {@code true} if this backend is expected to use Vector API (or other SIMD) internally.
     * This is informational and must not be used for control flow in hot paths.
     */
    boolean isVectorized();

    /**
     * Applies an affine 3x4 matrix to positions stored in SoA arrays.
     *
     * <p>For each {@code i in [offset, offset+count)}:
     *
     * <pre>
     * x' = m00*x + m01*y + m02*z + m03
     * y' = m10*x + m11*y + m12*z + m13
     * z' = m20*x + m21*y + m22*z + m23
     * </pre>
     *
     * The results are written back into {@code xs/ys/zs} in-place.
     */
    void transformAffine3x4SoA(
            float[] xs,
            float[] ys,
            float[] zs,
            int offset,
            int count,
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23
    );
}
