package github.kasuminova.prototypemachinery.modernbackend.accel;

import java.nio.IntBuffer;

/**
 * A tiny production helper that combines:
 * <ol>
 *   <li>bulk affine transform (SoA)</li>
 *   <li>tight packing into a BufferBuilder-like layout (AoS of 7 ints/vertex)</li>
 * </ol>
 *
 * <p>This lives in the base jar and does not reference Vector API. The selected backend may
 * be vectorized via the optional accel jar.
 */
public final class BatchedVertexPipeline {

    /** 7 ints/vertex: x y z u v color normal. */
    public static final int INTS_PER_VERTEX = 7;

    /** 28 bytes/vertex, matches many legacy POSITION_TEX_COLOR_NORMAL layouts. */
    public static final int BYTES_PER_VERTEX = INTS_PER_VERTEX * Integer.BYTES;

    /**
     * For tiny batches, the overhead of:
     * <ol>
     *   <li>writing transformed positions back to SoA arrays</li>
     *   <li>then running a second pass to pack</li>
     * </ol>
     * can dominate.
     *
     * <p>We therefore use a fused single-pass transform+pack loop when {@code count} is small.
     * This keeps pipeline fast for common micro-batches (e.g. one Gecko cube = 24 vertices),
     * while still letting large batches benefit from vectorized backends.
     */
    private static final int FUSED_PACK_THRESHOLD = Integer.getInteger("pm.pipeline.fusedPackThreshold", 96);

    private final PositionTransformBackend backend;

    public BatchedVertexPipeline(PositionTransformBackend backend) {
        if (backend == null) throw new NullPointerException("backend");
        this.backend = backend;
    }

    /** Uses the globally selected backend (vector if available, otherwise scalar). */
    public static BatchedVertexPipeline selected() {
        return new BatchedVertexPipeline(PositionTransformBackends.get());
    }

    /** Always scalar (useful for A/B testing). */
    public static BatchedVertexPipeline scalar() {
        return new BatchedVertexPipeline(ScalarPositionTransformBackend.INSTANCE);
    }

    public PositionTransformBackend backend() {
        return backend;
    }

    public String backendName() {
        return backend.name();
    }

    public boolean isVectorized() {
        return backend.isVectorized();
    }

    public void transformAffine3x4InPlace(
            float[] xs,
            float[] ys,
            float[] zs,
            int offset,
            int count,
            float m00, float m01, float m02, float m03,
            float m10, float m11, float m12, float m13,
            float m20, float m21, float m22, float m23
    ) {
        backend.transformAffine3x4SoA(
                xs, ys, zs,
                offset, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
        );
    }

    /**
     * Packs SoA vertex attributes into an {@code int[]} AoS output.
     *
     * <p>Layout per vertex: xBits, yBits, zBits, uBits, vBits, color, normal.
     */
    public static void packPositionUvColorNormalIntArray(
            float[] xs,
            float[] ys,
            float[] zs,
            float[] us,
            float[] vs,
            int[] colors,
            int[] normals,
            int vertexOffset,
            int count,
            int[] out,
            int outIntOffset
    ) {
        final int end = vertexOffset + count;
        int o = outIntOffset;
        for (int i = vertexOffset; i < end; i++) {
            out[o] = Float.floatToRawIntBits(xs[i]);
            out[o + 1] = Float.floatToRawIntBits(ys[i]);
            out[o + 2] = Float.floatToRawIntBits(zs[i]);
            out[o + 3] = Float.floatToRawIntBits(us[i]);
            out[o + 4] = Float.floatToRawIntBits(vs[i]);
            out[o + 5] = colors[i];
            out[o + 6] = normals[i];
            o += INTS_PER_VERTEX;
        }
    }

    /**
     * Packs SoA vertex attributes into an {@link IntBuffer} AoS output using absolute puts.
     *
     * <p>Layout per vertex: xBits, yBits, zBits, uBits, vBits, color, normal.
     */
    public static void packPositionUvColorNormalIntBuffer(
            float[] xs,
            float[] ys,
            float[] zs,
            float[] us,
            float[] vs,
            int[] colors,
            int[] normals,
            int vertexOffset,
            int count,
            IntBuffer out,
            int outIntOffset
    ) {
        final int end = vertexOffset + count;
        int o = outIntOffset;
        for (int i = vertexOffset; i < end; i++) {
            out.put(o, Float.floatToRawIntBits(xs[i]));
            out.put(o + 1, Float.floatToRawIntBits(ys[i]));
            out.put(o + 2, Float.floatToRawIntBits(zs[i]));
            out.put(o + 3, Float.floatToRawIntBits(us[i]));
            out.put(o + 4, Float.floatToRawIntBits(vs[i]));
            out.put(o + 5, colors[i]);
            out.put(o + 6, normals[i]);
            o += INTS_PER_VERTEX;
        }
    }

    /** Convenience: transform in place, then pack to int[]. */
    public void transformThenPackIntArray(
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
    ) {
        if (count <= 0) {
            return;
        }

        // Fast path for tiny batches: fused transform+pack without writing back xs/ys/zs.
        if (count <= FUSED_PACK_THRESHOLD) {
            transformThenPackIntArrayFused(
                xs, ys, zs,
                us, vs,
                colors, normals,
                vertexOffset, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                out, outIntOffset
            );
            return;
        }

        transformAffine3x4InPlace(
                xs, ys, zs,
                vertexOffset, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
        );
        packPositionUvColorNormalIntArray(
                xs, ys, zs,
                us, vs,
                colors, normals,
                vertexOffset, count,
                out, outIntOffset
        );
    }

    /** Convenience: transform in place, then pack to IntBuffer. */
    public void transformThenPackIntBuffer(
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
            IntBuffer out,
            int outIntOffset
    ) {
        if (count <= 0) {
            return;
        }

        // NOTE: for IntBuffer, we keep the existing 2-pass path. The fused AoS write is less
        // compelling here because IntBuffer absolute puts are relatively expensive and we would
        // still need 7 puts/vertex.
        transformAffine3x4InPlace(
                xs, ys, zs,
                vertexOffset, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
        );
        packPositionUvColorNormalIntBuffer(
                xs, ys, zs,
                us, vs,
                colors, normals,
                vertexOffset, count,
                out, outIntOffset
        );
    }

    /**
     * Fused single-pass transform+pack to AoS int[] output.
     *
     * <p>This avoids writing transformed positions back into {@code xs/ys/zs} and avoids the
     * second packing pass.
     */
    private static void transformThenPackIntArrayFused(
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
    ) {
        final int end = vertexOffset + count;
        int o = outIntOffset;
        for (int i = vertexOffset; i < end; i++) {
            final float x = xs[i];
            final float y = ys[i];
            final float z = zs[i];

            final float rx = (m00 * x) + (m01 * y) + (m02 * z) + m03;
            final float ry = (m10 * x) + (m11 * y) + (m12 * z) + m13;
            final float rz = (m20 * x) + (m21 * y) + (m22 * z) + m23;

            out[o] = Float.floatToRawIntBits(rx);
            out[o + 1] = Float.floatToRawIntBits(ry);
            out[o + 2] = Float.floatToRawIntBits(rz);
            out[o + 3] = Float.floatToRawIntBits(us[i]);
            out[o + 4] = Float.floatToRawIntBits(vs[i]);
            out[o + 5] = colors[i];
            out[o + 6] = normals[i];
            o += INTS_PER_VERTEX;
        }
    }
}
