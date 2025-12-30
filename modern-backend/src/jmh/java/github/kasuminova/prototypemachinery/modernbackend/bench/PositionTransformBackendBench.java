package github.kasuminova.prototypemachinery.modernbackend.bench;

import github.kasuminova.prototypemachinery.modernbackend.accel.PositionTransformBackend;
import github.kasuminova.prototypemachinery.modernbackend.accel.PositionTransformBackends;
import github.kasuminova.prototypemachinery.modernbackend.accel.ScalarPositionTransformBackend;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class PositionTransformBackendBench {

    @Param({"256", "4096", "65536"})
    public int count;

    private float[] xs;
    private float[] ys;
    private float[] zs;

    private PositionTransformBackend selected;

    // A fixed-ish affine 3x4 transform (m03/m13/m23 act like translation)
    private float m00, m01, m02, m03;
    private float m10, m11, m12, m13;
    private float m20, m21, m22, m23;

    @Setup(Level.Trial)
    public void setupTrial() {
        xs = new float[count];
        ys = new float[count];
        zs = new float[count];

        // Non-trivial values so the JIT can't fold everything.
        m00 = 1.03125f; m01 = 0.125f;   m02 = -0.0625f; m03 = 12.5f;
        m10 = -0.25f;   m11 = 0.9375f;  m12 = 0.03125f; m13 = -7.75f;
        m20 = 0.0625f;  m21 = -0.125f;  m22 = 1.015625f; m23 = 3.125f;

        selected = PositionTransformBackends.get();

        // Print once per trial for sanity (won't affect measured loop).
        System.out.println("[JMH] PositionTransform backend: " + selected.name() + ", vectorized=" + selected.isVectorized());
    }

    @Setup(Level.Iteration)
    public void setupIteration() {
        // Refill inputs each iteration to avoid trivially repeated data patterns.
        final Random r = new Random(12345L);
        for (int i = 0; i < count; i++) {
            xs[i] = (r.nextFloat() * 2.0f) - 1.0f;
            ys[i] = (r.nextFloat() * 2.0f) - 1.0f;
            zs[i] = (r.nextFloat() * 2.0f) - 1.0f;
        }
    }

    @Benchmark
    public void selected_backend_inplace(Blackhole bh) {
        selected.transformAffine3x4SoA(
                xs, ys, zs,
                0, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
        );

        // Consume a few values to avoid DCE.
        bh.consume(xs[0]);
        bh.consume(ys[count - 1]);
        bh.consume(zs[count >>> 1]);
    }

    @Benchmark
    public void scalar_direct_inplace(Blackhole bh) {
        ScalarPositionTransformBackend.INSTANCE.transformAffine3x4SoA(
                xs, ys, zs,
                0, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
        );

        bh.consume(xs[0]);
        bh.consume(ys[count - 1]);
        bh.consume(zs[count >>> 1]);
    }
}
