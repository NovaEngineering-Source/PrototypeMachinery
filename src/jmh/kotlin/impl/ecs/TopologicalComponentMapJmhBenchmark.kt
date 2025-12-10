package github.kasuminova.prototypemachinery.impl.ecs

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import java.util.Random

@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class TopologicalComponentMapJmhBenchmark {

    private var map: TopologicalComponentMapImpl<Int, String> = TopologicalComponentMapImpl()
    private val random = Random(12345)

    @Setup(Level.Iteration)
    fun setup() {
        map = TopologicalComponentMapImpl()
    }

    @Benchmark
    fun benchmarkLargeLinearChain() {
        val localMap = TopologicalComponentMapImpl<Int, String>()
        val count = 5000
        for (i in 0 until count) {
            val deps = if (i > 0) setOf(i - 1) else emptySet()
            localMap.add(i, "Comp$i", deps)
        }
        val order = localMap.orderedComponents
        if (order.size != count) throw IllegalStateException("Wrong size")
    }

    @Benchmark
    fun benchmarkIndependentNodes() {
        val localMap = TopologicalComponentMapImpl<Int, String>()
        val count = 10000
        for (i in 0 until count) {
            localMap.add(i, "Comp$i", emptySet())
        }
        val order = localMap.orderedComponents
        if (order.size != count) throw IllegalStateException("Wrong size")
    }

    @Benchmark
    fun benchmarkComplexGraph() {
        val localMap = TopologicalComponentMapImpl<Int, String>()
        val count = 2000
        // Use a local random to avoid sharing state, though Scope.Thread handles it.
        // Re-seeding for consistency
        val localRandom = Random(12345)
        
        for (i in 0 until count) {
            val deps = mutableSetOf<Int>()
            if (i > 0) {
                val numDeps = localRandom.nextInt(Math.min(i, 5)) + 1
                repeat(numDeps) {
                    deps.add(localRandom.nextInt(i))
                }
            }
            localMap.add(i, "Comp$i", deps)
        }
        val order = localMap.orderedComponents
        if (order.size != count) throw IllegalStateException("Wrong size")
    }
}
