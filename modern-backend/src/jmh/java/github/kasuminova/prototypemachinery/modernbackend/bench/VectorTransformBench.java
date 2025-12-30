package github.kasuminova.prototypemachinery.modernbackend.bench;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorSpecies;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;

/**
 * Microbench: compare scalar vs Vector API for a typical "many vertices * same matrix" transform.
 *
 * Notes:
 * - This benchmark uses SoA (separate x/y/z arrays). Vector API is most effective in this layout.
 * - Real MC 1.12 BufferBuilder hot paths are often dominated by per-vertex packing and ByteBuffer writes;
 *   this isolates the math portion to estimate the ceiling of Vector API benefits.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
@State(Scope.Thread)
public class VectorTransformBench {

    /** Vertex count. Includes small sizes (4) and batch sizes where Vector becomes meaningful. */
    @Param({"4", "256", "4096", "65536"})
    public int n;

    // Inputs (SoA)
    private float[] xs;
    private float[] ys;
    private float[] zs;

    // Outputs
    private float[] ox;
    private float[] oy;
    private float[] oz;

    // 3x4 affine matrix (row-major):
    // [ m00 m01 m02 m03 ]
    // [ m10 m11 m12 m13 ]
    // [ m20 m21 m22 m23 ]
    private float m00, m01, m02, m03;
    private float m10, m11, m12, m13;
    private float m20, m21, m22, m23;

    @Setup(Level.Trial)
    public void setup() {
        xs = new float[n];
        ys = new float[n];
        zs = new float[n];
        ox = new float[n];
        oy = new float[n];
        oz = new float[n];

        SplittableRandom r = new SplittableRandom(12345);
        for (int i = 0; i < n; i++) {
            xs[i] = (float) (r.nextDouble(-2.0, 2.0));
            ys[i] = (float) (r.nextDouble(-2.0, 2.0));
            zs[i] = (float) (r.nextDouble(-2.0, 2.0));
        }

        // A stable, non-trivial affine transform.
        m00 = 0.93f;  m01 = 0.02f;  m02 = -0.11f; m03 = 0.15f;
        m10 = -0.07f; m11 = 1.05f;  m12 = 0.03f;  m13 = -0.21f;
        m20 = 0.13f;  m21 = -0.04f; m22 = 0.97f;  m23 = 0.08f;
    }

    @Benchmark
    public void scalar_transform(Blackhole bh) {
        // Typical scalar loop.
        for (int i = 0; i < n; i++) {
            float x = xs[i];
            float y = ys[i];
            float z = zs[i];

            ox[i] = m00 * x + m01 * y + m02 * z + m03;
            oy[i] = m10 * x + m11 * y + m12 * z + m13;
            oz[i] = m20 * x + m21 * y + m22 * z + m23;
        }
        // Prevent dead-code elimination.
        bh.consume(ox[n - 1]);
        bh.consume(oy[n - 1]);
        bh.consume(oz[n - 1]);
    }

    @Benchmark
    public void vector_transform_preferred(Blackhole bh) {
        final VectorSpecies<Float> sp = FloatVector.SPECIES_PREFERRED;
        final int step = sp.length();

        final FloatVector vm00 = FloatVector.broadcast(sp, m00);
        final FloatVector vm01 = FloatVector.broadcast(sp, m01);
        final FloatVector vm02 = FloatVector.broadcast(sp, m02);
        final FloatVector vm03 = FloatVector.broadcast(sp, m03);

        final FloatVector vm10 = FloatVector.broadcast(sp, m10);
        final FloatVector vm11 = FloatVector.broadcast(sp, m11);
        final FloatVector vm12 = FloatVector.broadcast(sp, m12);
        final FloatVector vm13 = FloatVector.broadcast(sp, m13);

        final FloatVector vm20 = FloatVector.broadcast(sp, m20);
        final FloatVector vm21 = FloatVector.broadcast(sp, m21);
        final FloatVector vm22 = FloatVector.broadcast(sp, m22);
        final FloatVector vm23 = FloatVector.broadcast(sp, m23);

        int i = 0;
        final int upper = n - (n % step);
        for (; i < upper; i += step) {
            FloatVector vx = FloatVector.fromArray(sp, xs, i);
            FloatVector vy = FloatVector.fromArray(sp, ys, i);
            FloatVector vz = FloatVector.fromArray(sp, zs, i);

            // outX = m00*x + m01*y + m02*z + m03
            FloatVector outX = vx.mul(vm00).add(vy.mul(vm01)).add(vz.mul(vm02)).add(vm03);
            FloatVector outY = vx.mul(vm10).add(vy.mul(vm11)).add(vz.mul(vm12)).add(vm13);
            FloatVector outZ = vx.mul(vm20).add(vy.mul(vm21)).add(vz.mul(vm22)).add(vm23);

            outX.intoArray(ox, i);
            outY.intoArray(oy, i);
            outZ.intoArray(oz, i);
        }

        // Tail.
        for (; i < n; i++) {
            float x = xs[i];
            float y = ys[i];
            float z = zs[i];

            ox[i] = m00 * x + m01 * y + m02 * z + m03;
            oy[i] = m10 * x + m11 * y + m12 * z + m13;
            oz[i] = m20 * x + m21 * y + m22 * z + m23;
        }

        bh.consume(ox[n - 1]);
        bh.consume(oy[n - 1]);
        bh.consume(oz[n - 1]);
    }
}
