package github.kasuminova.prototypemachinery.client.impl.render.gecko

import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import javax.vecmath.Matrix3f
import javax.vecmath.Matrix4f
import javax.vecmath.Vector3f
import javax.vecmath.Vector4f

/**
 * Microbenchmarks for the hottest CPU-side math in Gecko baking:
 * - modelMatrix * position (x,y,z,1)
 * - normalMatrix * normal (x,y,z)
 *
 * This helps answer: "Is the manual multiply faster than vecmath transform() + allocations?"
 *
 * Notes:
 * - This benchmark intentionally avoids Minecraft BufferBuilder calls and focuses on math + allocations.
 * - Numbers are only meaningful relative to each other on the same machine/JVM.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
open class GeckoMatrixTransformJmhBenchmark {

    @State(Scope.Thread)
    open class S {
        /** Total vertices processed per invocation. Must be a multiple of 4 (quad). */
        @Param("1024", "8192", "65536")
        var vertices: Int = 1024

        lateinit var model: Matrix4f
        lateinit var normal: Matrix3f

        lateinit var posX: FloatArray
        lateinit var posY: FloatArray
        lateinit var posZ: FloatArray

        lateinit var nX: FloatArray
        lateinit var nY: FloatArray
        lateinit var nZ: FloatArray

        // Reused objects for the "no allocation" baselines.
        lateinit var tmpV4: Vector4f
        lateinit var tmpN3: Vector3f

        @Setup(Level.Trial)
        fun setup() {
            // A stable, non-trivial affine matrix (rotation-ish + translation)
            model = Matrix4f(
                0.70710677f, 0.0f, 0.70710677f, 0.25f,
                0.0f, 1.0f, 0.0f, 0.50f,
                -0.70710677f, 0.0f, 0.70710677f, -0.75f,
                0.0f, 0.0f, 0.0f, 1.0f
            )

            normal = Matrix3f(
                0.70710677f, 0.0f, 0.70710677f,
                0.0f, 1.0f, 0.0f,
                -0.70710677f, 0.0f, 0.70710677f
            )

            val v = vertices.coerceAtLeast(4)
            val quads = v / 4

            posX = FloatArray(v)
            posY = FloatArray(v)
            posZ = FloatArray(v)

            // Per-quad normals (mirrors Gecko: one normal per quad, reused for 4 vertices)
            nX = FloatArray(quads)
            nY = FloatArray(quads)
            nZ = FloatArray(quads)

            // Deterministic pseudo-data without Random (avoid measuring RNG).
            for (i in 0 until v) {
                posX[i] = ((i * 17) % 1024) / 1024.0f - 0.5f
                posY[i] = ((i * 31) % 1024) / 1024.0f - 0.5f
                posZ[i] = ((i * 47) % 1024) / 1024.0f - 0.5f
            }
            for (q in 0 until quads) {
                // Cycle a few unit-ish normals
                when (q and 3) {
                    0 -> {
                        nX[q] = 1f; nY[q] = 0f; nZ[q] = 0f
                    }
                    1 -> {
                        nX[q] = 0f; nY[q] = 1f; nZ[q] = 0f
                    }
                    2 -> {
                        nX[q] = 0f; nY[q] = 0f; nZ[q] = 1f
                    }
                    else -> {
                        nX[q] = 0.57735026f; nY[q] = 0.57735026f; nZ[q] = 0.57735026f
                    }
                }
            }

            tmpV4 = Vector4f()
            tmpN3 = Vector3f()
        }
    }

    /** Baseline similar to the old GeckoModelBaker hot path: alloc Vector4f per vertex + Matrix4f.transform. */
    @Benchmark
    fun model_transform_allocVector4f(state: S, bh: Blackhole) {
        val m = state.model
        var acc = 0.0f
        val xArr = state.posX
        val yArr = state.posY
        val zArr = state.posZ

        for (i in 0 until state.vertices) {
            val v = Vector4f(xArr[i], yArr[i], zArr[i], 1.0f)
            m.transform(v)
            acc += v.x + v.y + v.z
        }

        bh.consume(acc)
    }

    /** Baseline without allocations: reuse Vector4f, still using Matrix4f.transform. */
    @Benchmark
    fun model_transform_reuseVector4f(state: S, bh: Blackhole) {
        val m = state.model
        val v = state.tmpV4
        var acc = 0.0f
        val xArr = state.posX
        val yArr = state.posY
        val zArr = state.posZ

        for (i in 0 until state.vertices) {
            v.set(xArr[i], yArr[i], zArr[i], 1.0f)
            m.transform(v)
            acc += v.x + v.y + v.z
        }

        bh.consume(acc)
    }

    /** Manual affine multiply equivalent to the current GeckoModelBaker.emitVertex implementation. */
    @Benchmark
    fun model_manual_affine(state: S, bh: Blackhole) {
        val m = state.model
        var acc = 0.0f
        val xArr = state.posX
        val yArr = state.posY
        val zArr = state.posZ

        for (i in 0 until state.vertices) {
            val x = xArr[i]
            val y = yArr[i]
            val z = zArr[i]

            val tx = m.m00 * x + m.m01 * y + m.m02 * z + m.m03
            val ty = m.m10 * x + m.m11 * y + m.m12 * z + m.m13
            val tz = m.m20 * x + m.m21 * y + m.m22 * z + m.m23

            acc += tx + ty + tz
        }

        bh.consume(acc)
    }

    /** Baseline similar to old normal path: per-quad Vector3f alloc + Matrix3f.transform, then reused for 4 vertices. */
    @Benchmark
    fun normal_transform_allocVector3f_perQuad(state: S, bh: Blackhole) {
        val n = state.normal
        val quads = state.vertices / 4
        var acc = 0.0f

        for (q in 0 until quads) {
            val v = Vector3f(state.nX[q], state.nY[q], state.nZ[q])
            n.transform(v)

            // Use it 4x like a quad.
            acc += (v.x + v.y + v.z) * 4.0f
        }

        bh.consume(acc)
    }

    /** Baseline without allocations: reuse Vector3f, still using Matrix3f.transform. */
    @Benchmark
    fun normal_transform_reuseVector3f_perQuad(state: S, bh: Blackhole) {
        val n = state.normal
        val v = state.tmpN3
        val quads = state.vertices / 4
        var acc = 0.0f

        for (q in 0 until quads) {
            v.set(state.nX[q], state.nY[q], state.nZ[q])
            n.transform(v)
            acc += (v.x + v.y + v.z) * 4.0f
        }

        bh.consume(acc)
    }

    /** Manual 3x3 multiply equivalent to GeckoModelBaker.renderQuad normal path (per-quad). */
    @Benchmark
    fun normal_manual_mul_perQuad(state: S, bh: Blackhole) {
        val n = state.normal
        val quads = state.vertices / 4
        var acc = 0.0f

        for (q in 0 until quads) {
            val nx = state.nX[q]
            val ny = state.nY[q]
            val nz = state.nZ[q]

            val tx = n.m00 * nx + n.m01 * ny + n.m02 * nz
            val ty = n.m10 * nx + n.m11 * ny + n.m12 * nz
            val tz = n.m20 * nx + n.m21 * ny + n.m22 * nz

            acc += (tx + ty + tz) * 4.0f
        }

        bh.consume(acc)
    }
}
