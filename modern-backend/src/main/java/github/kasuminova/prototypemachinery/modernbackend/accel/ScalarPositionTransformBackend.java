package github.kasuminova.prototypemachinery.modernbackend.accel;

/** Default scalar backend that is always available. */
public final class ScalarPositionTransformBackend implements PositionTransformBackend {

    public static final ScalarPositionTransformBackend INSTANCE = new ScalarPositionTransformBackend();

    private ScalarPositionTransformBackend() {
    }

    @Override
    public String name() {
        return "scalar";
    }

    @Override
    public boolean isVectorized() {
        return false;
    }

    @Override
    public void transformAffine3x4SoA(
            float[] xs,
            float[] ys,
            float[] zs,
            int offset,
            int count,
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23
    ) {
        // Intentionally minimal: single tight loop, no allocations, no range checks beyond what the JVM will do.
        final int end = offset + count;
        for (int i = offset; i < end; i++) {
            final float x = xs[i];
            final float y = ys[i];
            final float z = zs[i];

            xs[i] = (m00 * x) + (m01 * y) + (m02 * z) + m03;
            ys[i] = (m10 * x) + (m11 * y) + (m12 * z) + m13;
            zs[i] = (m20 * x) + (m21 * y) + (m22 * z) + m23;
        }
    }
}
