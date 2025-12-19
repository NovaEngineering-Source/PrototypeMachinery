package github.kasuminova.prototypemachinery.impl.scheduler

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.annotations.Warmup
import org.openjdk.jmh.infra.Blackhole
import java.util.Random
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * A more realistic "one tick" scheduling benchmark based on the workload profile:
 * - ~10% main-thread tasks (sequential)
 * - ~20% affinity-grouped tasks (serialized on single-thread lanes)
 * - ~70% fully concurrent tasks (thread pool)
 *
 * Each task "cost" is simulated as a busy-spin between [minTaskMicros]..[maxTaskMicros].
 * This is meant to approximate microsecond-scale work (5~1000us).
 *
 * 更贴近实际 tick 的调度基准：
 * - 10% 主线程任务（串行）
 * - 20% Affinity 分组任务（分组后在 lane 单线程串行）
 * - 70% 并发任务（线程池）
 *
 * 注意：这里用 busy-spin 模拟每个任务的微秒级耗时。
 * 在 JMH 下时间型 busy-spin 会引入噪声，但它更接近“真实耗时分布”，
 * 适合回答“这种任务分布下，协程调度 vs Java 调度是否更划算”。
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
open class TaskSchedulerMixedWorkloadJmhBenchmark {

    /** Total tasks per tick / 每 tick 总任务数 */
    @Param("500", "2000")
    var totalTasks: Int = 500

    /** Main thread share (0.10 = 10%) / 主线程占比 */
    @Param("0.10")
    var mainShare: Double = 0.10

    /** Affinity share (0.20 = 20%) / affinity 分组占比 */
    @Param("0.20")
    var affinityShare: Double = 0.20

    /** Concurrent share (0.70 = 70%) / 并发占比 */
    @Param("0.70")
    var concurrentShare: Double = 0.70

    /** Min simulated task duration in microseconds / 任务最小耗时（微秒） */
    @Param("5")
    var minTaskMicros: Int = 5

    /** Max simulated task duration in microseconds / 任务最大耗时（微秒） */
    @Param("1000")
    var maxTaskMicros: Int = 1000

    /** How many distinct affinity groups inside the affinity share / affinity 组数量 */
    @Param("32")
    var affinityGroups: Int = 32

    /** How many lane executors (single-thread) / lane 数量 */
    @Param("4")
    var laneCount: Int = 4

    private lateinit var pool: ExecutorService
    private lateinit var poolDispatcher: CoroutineDispatcher

    private lateinit var lanes: Array<ExecutorService>
    private lateinit var laneDispatchers: Array<CoroutineDispatcher>

    // Pre-generated per-task durations (nanoseconds) for one "tick".
    private lateinit var durationsNanos: LongArray

    // Pre-generated affinity group assignment for the affinity slice.
    private lateinit var affinityGroupForIndex: IntArray

    @Setup(Level.Trial)
    fun setupTrial() {
        // Use fixed pool for determinism and similarity with current scheduler.
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        pool = Executors.newFixedThreadPool(threads)
        poolDispatcher = pool.asCoroutineDispatcher()

        val lanesClamped = laneCount.coerceIn(1, 32)
        lanes = Array(lanesClamped) { Executors.newSingleThreadExecutor() }
        laneDispatchers = Array(lanesClamped) { i -> lanes[i].asCoroutineDispatcher() }
    }

    @Setup(Level.Iteration)
    fun setupIteration() {
        // Sanity check for shares
        val sum = mainShare + affinityShare + concurrentShare
        require(sum > 0.999 && sum < 1.001) { "Shares must sum to 1.0 (got $sum)" }

        val rnd = Random(12345L)

        durationsNanos = LongArray(totalTasks)
        for (i in 0 until totalTasks) {
            durationsNanos[i] = sampleLogUniformMicros(rnd, minTaskMicros, maxTaskMicros) * 1_000L
        }

        val affinityCount = (totalTasks * affinityShare).toInt()
        affinityGroupForIndex = IntArray(affinityCount)
        for (i in 0 until affinityCount) {
            affinityGroupForIndex[i] = rnd.nextInt(affinityGroups.coerceAtLeast(1))
        }
    }

    @TearDown(Level.Trial)
    fun tearDownTrial() {
        for (l in lanes) {
            l.shutdownNow()
        }
        pool.shutdownNow()
    }

    @Benchmark
    fun java_mixedWorkload_submitAndJoin(bh: Blackhole) {
        val mainCount = (totalTasks * mainShare).toInt().coerceAtMost(totalTasks)
        val affinityCount = (totalTasks * affinityShare).toInt().coerceAtMost(totalTasks - mainCount)
        val concurrentCount = totalTasks - mainCount - affinityCount

        // 1) main-thread work (sequential)
        for (i in 0 until mainCount) {
            busySpinNanos(durationsNanos[i], bh)
        }

        // 2) submit affinity groups onto single-thread lanes
        // Similar to current scheduler: one lane task runs a group serially.
        val laneFutures = ArrayList<Future<*>>(affinityGroups)
        if (affinityCount > 0) {
            val groups: Array<MutableList<Int>> = Array(affinityGroups.coerceAtLeast(1)) { ArrayList() }
            for (i in 0 until affinityCount) {
                groups[affinityGroupForIndex[i]].add(i)
            }

            for (g in groups.indices) {
                val idxs = groups[g]
                if (idxs.isEmpty()) continue

                val laneIdx = (g and Int.MAX_VALUE) % lanes.size
                val lane = lanes[laneIdx]

                laneFutures += lane.submit {
                    // affinity slice uses durations from [mainCount, mainCount + affinityCount)
                    for (local in idxs) {
                        val globalIndex = mainCount + local
                        busySpinNanos(durationsNanos[globalIndex], bh)
                    }
                }
            }
        }

        // 3) submit fully concurrent tasks onto pool
        val poolFutures = ArrayList<Future<*>>(concurrentCount)
        val concurrentStart = mainCount + affinityCount
        for (i in 0 until concurrentCount) {
            val globalIndex = concurrentStart + i
            poolFutures += pool.submit {
                busySpinNanos(durationsNanos[globalIndex], bh)
            }
        }

        // 4) join barrier (wait for all concurrent work this tick)
        for (f in laneFutures) f.get()
        for (f in poolFutures) f.get()
    }

    @Benchmark
    fun coroutines_mixedWorkload_launchAndJoinAll(bh: Blackhole) {
        val mainCount = (totalTasks * mainShare).toInt().coerceAtMost(totalTasks)
        val affinityCount = (totalTasks * affinityShare).toInt().coerceAtMost(totalTasks - mainCount)
        val concurrentCount = totalTasks - mainCount - affinityCount

        // Use runBlocking to model the main-thread tick barrier.
        runBlocking {
            // 1) main-thread work (sequential)
            for (i in 0 until mainCount) {
                busySpinNanos(durationsNanos[i], bh)
            }

            val scope = CoroutineScope(coroutineContext)
            val jobs = ArrayList<Job>(affinityGroups + concurrentCount)

            // 2) affinity: group -> lane dispatcher, serialized within group
            if (affinityCount > 0) {
                val groups: Array<MutableList<Int>> = Array(affinityGroups.coerceAtLeast(1)) { ArrayList() }
                for (i in 0 until affinityCount) {
                    groups[affinityGroupForIndex[i]].add(i)
                }

                for (g in groups.indices) {
                    val idxs = groups[g]
                    if (idxs.isEmpty()) continue

                    val laneIdx = (g and Int.MAX_VALUE) % laneDispatchers.size
                    val laneDispatcher = laneDispatchers[laneIdx]

                    jobs += scope.launch(laneDispatcher) {
                        for (local in idxs) {
                            val globalIndex = mainCount + local
                            busySpinNanos(durationsNanos[globalIndex], bh)
                        }
                    }
                }
            }

            // 3) fully concurrent tasks on pool dispatcher
            val concurrentStart = mainCount + affinityCount
            repeat(concurrentCount) { i ->
                val globalIndex = concurrentStart + i
                jobs += scope.launch(poolDispatcher) {
                    busySpinNanos(durationsNanos[globalIndex], bh)
                }
            }

            // 4) join barrier
            jobs.joinAll()
        }
    }

    private fun busySpinNanos(targetNanos: Long, bh: Blackhole) {
        val start = System.nanoTime()
        // Busy spin until target time has passed.
        // Use a tiny blackhole consume to prevent over-aggressive optimization.
        while (System.nanoTime() - start < targetNanos) {
            bh.consume(1)
        }
    }

    private fun sampleLogUniformMicros(rnd: Random, min: Int, max: Int): Long {
        val lo = min.coerceAtLeast(1)
        val hi = max.coerceAtLeast(lo)
        if (lo == hi) return lo.toLong()

        // log-uniform distribution: better matches "many small tasks, few large tasks".
        val logLo = kotlin.math.ln(lo.toDouble())
        val logHi = kotlin.math.ln(hi.toDouble())
        val u = rnd.nextDouble()
        val v = kotlin.math.exp(logLo + (logHi - logLo) * u)
        return v.toLong().coerceIn(lo.toLong(), hi.toLong())
    }
}
