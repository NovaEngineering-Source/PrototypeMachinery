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
import software.bernie.geckolib3.util.MatrixStack
import java.util.concurrent.TimeUnit

/**
 * Benchmarks GeckoLib's MatrixStack implementation (software.bernie.geckolib3.util.MatrixStack).
 *
 * It answers:
 * - How expensive are push/pop (note: GeckoLib allocates new Matrix4f/Matrix3f on every push)?
 * - What is the per-node cost of translate/rotate/scale as used in GeckoModelBaker recursion?
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 3, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Fork(1)
open class GeckoMatrixStackJmhBenchmark {

    @State(Scope.Thread)
    open class S {
        /** Number of simulated nodes (bones/cubes) per invocation. */
        @Param("128", "1024", "8192")
        var nodes: Int = 128

        lateinit var ms: MatrixStack

        @Setup(Level.Iteration)
        fun setupIteration() {
            ms = MatrixStack()
        }
    }

    /**
     * Simulates the recursion hot path used by GeckoModelBaker:
     * push -> translate -> rotateX/Y/Z -> scale -> pop.
     */
    @Benchmark
    fun push_transform_pop(state: S, bh: Blackhole) {
        val ms = state.ms
        val n = state.nodes

        for (i in 0 until n) {
            ms.push()
            // small deterministic transforms to avoid being optimized away
            val fx = (i and 15) * 0.001f
            ms.translate(fx, fx * 2f, fx * 3f)
            ms.rotateX(0.01f)
            ms.rotateY(0.02f)
            ms.rotateZ(0.03f)
            ms.scale(1.0f, 1.0f, 1.0f)
            // consume a value that depends on the current matrix
            bh.consume(ms.modelMatrix.m03)
            ms.pop()
        }
    }

    /** Only push/pop, to isolate allocation+Stack overhead. */
    @Benchmark
    fun push_pop_only(state: S, bh: Blackhole) {
        val ms = state.ms
        val n = state.nodes

        for (i in 0 until n) {
            ms.push()
            bh.consume(ms.modelMatrix.m00)
            ms.pop()
        }
    }

    /**
     * No push/pop: same transforms applied repeatedly on a single level.
     * This is a lower bound for the math itself.
     */
    @Benchmark
    fun transform_only_no_push(state: S, bh: Blackhole) {
        val ms = state.ms
        val n = state.nodes

        for (i in 0 until n) {
            val fx = (i and 15) * 0.001f
            ms.translate(fx, fx * 2f, fx * 3f)
            ms.rotateX(0.01f)
            ms.rotateY(0.02f)
            ms.rotateZ(0.03f)
            ms.scale(1.0f, 1.0f, 1.0f)
            bh.consume(ms.normalMatrix.m00)
        }
    }
}
