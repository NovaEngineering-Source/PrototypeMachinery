package github.kasuminova.prototypemachinery.modernbackend.bench;

import github.kasuminova.prototypemachinery.modernbackend.accel.BatchedVertexPipeline;
import github.kasuminova.prototypemachinery.modernbackend.accel.PositionTransformBackend;
import github.kasuminova.prototypemachinery.modernbackend.accel.PositionTransformBackends;
import github.kasuminova.prototypemachinery.modernbackend.accel.ScalarPositionTransformBackend;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * A more end-to-end style microbench:
 *  1) bulk transform positions (SoA)
 *  2) pack + write into a BufferBuilder-like ByteBuffer layout (AoS)
 *
 * This models the idea of replacing many tiny BufferBuilder calls with:
 *   batch math (vectorizable) + tight packing loop.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class BatchedVertexPipelineBench {

    // 28 bytes/vertex: pos(12) + uv(8) + color(4) + normal(4)
    private static final int STRIDE = 28;

    @Param({"256", "4096", "65536"})
    public int count;

    private float[] xs;
    private float[] ys;
    private float[] zs;
    private float[] us;
    private float[] vs;
    private int[] colors;
    private int[] normals;

    private ByteBuffer buf;
    private IntBuffer intView;
    private int[] packedInts;

    private PositionTransformBackend selected;
    private BatchedVertexPipeline pipelineSelected;
    private BatchedVertexPipeline pipelineScalar;

    private float m00, m01, m02, m03;
    private float m10, m11, m12, m13;
    private float m20, m21, m22, m23;

    @Setup(Level.Trial)
    public void setupTrial() {
        xs = new float[count];
        ys = new float[count];
        zs = new float[count];
        us = new float[count];
        vs = new float[count];
        colors = new int[count];
        normals = new int[count];

        buf = ByteBuffer.allocateDirect(count * STRIDE).order(ByteOrder.LITTLE_ENDIAN);
        intView = buf.asIntBuffer();
        packedInts = new int[count * (STRIDE / 4)];

        m00 = 1.03125f; m01 = 0.125f;   m02 = -0.0625f; m03 = 12.5f;
        m10 = -0.25f;   m11 = 0.9375f;  m12 = 0.03125f; m13 = -7.75f;
        m20 = 0.0625f;  m21 = -0.125f;  m22 = 1.015625f; m23 = 3.125f;

        selected = PositionTransformBackends.get();
        System.out.println("[JMH] Vertex pipeline backend: " + selected.name() + ", vectorized=" + selected.isVectorized());

        pipelineSelected = new BatchedVertexPipeline(selected);
        pipelineScalar = BatchedVertexPipeline.scalar();
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        final Random r = new Random(12345L);
        for (int i = 0; i < count; i++) {
            xs[i] = (r.nextFloat() * 2.0f) - 1.0f;
            ys[i] = (r.nextFloat() * 2.0f) - 1.0f;
            zs[i] = (r.nextFloat() * 2.0f) - 1.0f;
            us[i] = r.nextFloat();
            vs[i] = r.nextFloat();

            // Pre-packed ABGR (little-endian int write matches many legacy BufferBuilder layouts)
            final int a = 0xFF;
            final int b = (int) (r.nextFloat() * 255.0f);
            final int g = (int) (r.nextFloat() * 255.0f);
            final int rr = (int) (r.nextFloat() * 255.0f);
            colors[i] = (a << 24) | (b << 16) | (g << 8) | rr;

            // Fake packed normal
            normals[i] = 0x007F7F7F;
        }
    }

    @Benchmark
    public void scalar_transform_then_pack(Blackhole bh) {
        ScalarPositionTransformBackend.INSTANCE.transformAffine3x4SoA(
                xs, ys, zs,
                0, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
        );

        pack(bh);
    }

    @Benchmark
    public void selected_transform_then_pack(Blackhole bh) {
        selected.transformAffine3x4SoA(
                xs, ys, zs,
                0, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
        );

        pack(bh);
    }

    @Benchmark
    public void scalar_transform_then_pack_intbuffer(Blackhole bh) {
        pipelineScalar.transformThenPackIntBuffer(
            xs, ys, zs,
            us, vs,
            colors, normals,
            0, count,
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            intView,
            0
        );

        // DCE guard
        bh.consume(intView.get(5));
        bh.consume(intView.get((count - 1) * 7 + 6));
    }

    @Benchmark
    public void selected_transform_then_pack_intbuffer(Blackhole bh) {
        pipelineSelected.transformThenPackIntBuffer(
            xs, ys, zs,
            us, vs,
            colors, normals,
            0, count,
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            intView,
            0
        );

        // DCE guard
        bh.consume(intView.get(5));
        bh.consume(intView.get((count - 1) * 7 + 6));
    }

    @Benchmark
    public void scalar_transform_then_pack_intarray(Blackhole bh) {
        pipelineScalar.transformThenPackIntArray(
            xs, ys, zs,
            us, vs,
            colors, normals,
            0, count,
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            packedInts,
            0
        );

        // DCE guard
        bh.consume(packedInts[5]);
        bh.consume(packedInts[(count - 1) * 7 + 6]);
    }

    @Benchmark
    public void selected_transform_then_pack_intarray(Blackhole bh) {
        pipelineSelected.transformThenPackIntArray(
            xs, ys, zs,
            us, vs,
            colors, normals,
            0, count,
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
            packedInts,
            0
        );

        // DCE guard
        bh.consume(packedInts[5]);
        bh.consume(packedInts[(count - 1) * 7 + 6]);
    }

    private void pack(Blackhole bh) {
        final ByteBuffer b = buf;

        for (int i = 0; i < count; i++) {
            final int off = i * STRIDE;

            b.putFloat(off, xs[i]);
            b.putFloat(off + 4, ys[i]);
            b.putFloat(off + 8, zs[i]);

            b.putFloat(off + 12, us[i]);
            b.putFloat(off + 16, vs[i]);

            b.putInt(off + 20, colors[i]);
            b.putInt(off + 24, normals[i]);
        }

        // DCE guard
        bh.consume(b.getInt(20));
        bh.consume(b.getInt((count - 1) * STRIDE + 24));
    }

    // IntBuffer/int[] packing moved into BatchedVertexPipeline for production reuse.
}
