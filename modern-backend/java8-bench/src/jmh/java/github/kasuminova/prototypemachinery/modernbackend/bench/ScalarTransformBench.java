package github.kasuminova.prototypemachinery.modernbackend.bench;

import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

/**
 * Java 8-friendly scalar benchmark, meant to be compared across runtimes (JDK8/JDK21/JDK25)
 * while keeping bytecode level at 8.
 *
 * Notes:
 * - Data layout: SoA (separate x/y/z arrays), similar to the VectorTransformBench in modern-backend.
 * - Transform: simple affine matrix (3x4) applied to each element.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
@State(Scope.Thread)
public class ScalarTransformBench {

    @Param({"4", "256", "4096", "65536"})
    public int n;

    // Input
    float[] x;
    float[] y;
    float[] z;

    // Output
    float[] ox;
    float[] oy;
    float[] oz;

    // 3x4 affine (row-major):
    // [ m00 m01 m02 m03 ]
    // [ m10 m11 m12 m13 ]
    // [ m20 m21 m22 m23 ]
    float m00, m01, m02, m03;
    float m10, m11, m12, m13;
    float m20, m21, m22, m23;

    @Setup(Level.Trial)
    public void setup() {
        x = new float[n];
        y = new float[n];
        z = new float[n];
        ox = new float[n];
        oy = new float[n];
        oz = new float[n];

        // Deterministic pseudo data
        for (int i = 0; i < n; i++) {
            x[i] = (float) (i * 0.001 + 1.0);
            y[i] = (float) (i * 0.002 + 2.0);
            z[i] = (float) (i * 0.003 + 3.0);
        }

        // Affine-ish matrix; not orthonormal (avoid trivial simplifications).
        m00 = 1.01f; m01 = 0.02f; m02 = -0.03f; m03 = 4.0f;
        m10 = -0.04f; m11 = 0.99f; m12 = 0.05f; m13 = 5.0f;
        m20 = 0.06f; m21 = -0.07f; m22 = 1.02f; m23 = 6.0f;
    }

    @Benchmark
    public void scalar_transform() {
        // Hoist matrix loads into locals (helps JIT and keeps it comparable to hand-optimized loops).
        final float lm00 = m00, lm01 = m01, lm02 = m02, lm03 = m03;
        final float lm10 = m10, lm11 = m11, lm12 = m12, lm13 = m13;
        final float lm20 = m20, lm21 = m21, lm22 = m22, lm23 = m23;

        final float[] lx = x;
        final float[] ly = y;
        final float[] lz = z;
        final float[] lox = ox;
        final float[] loy = oy;
        final float[] loz = oz;

        for (int i = 0; i < n; i++) {
            final float px = lx[i];
            final float py = ly[i];
            final float pz = lz[i];

            lox[i] = lm00 * px + lm01 * py + lm02 * pz + lm03;
            loy[i] = lm10 * px + lm11 * py + lm12 * pz + lm13;
            loz[i] = lm20 * px + lm21 * py + lm22 * pz + lm23;
        }
    }
}
