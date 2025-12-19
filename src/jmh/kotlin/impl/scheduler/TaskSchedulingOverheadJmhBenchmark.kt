package github.kasuminova.prototypemachinery.impl.scheduler

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/**
 * Benchmark the *scheduling* overhead of:
 * - Java ExecutorService submit + Future.get join barrier
 * - Kotlin coroutines launch + joinAll join barrier (on the same underlying Executor)
 *
 * 用于基准测试“调度开销”（不是业务逻辑本身）：
 * - Java: ExecutorService.submit + Future.get（join 屏障）
 * - 协程: launch + joinAll（join 屏障，底层同一个 Executor）
 *
 * Notes / 注意：
 * - 这里的任务体非常小（consumeCPU(1)），主要看每 tick 大量任务的调度与 join 开销。
 * - 如果你想模拟真实机器逻辑，请把 workTokens 调大或替换为更贴近的 workload。
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
open class TaskSchedulingOverheadJmhBenchmark {

    /** Number of tasks per tick / 每 tick 的任务数量 */
    @Param("500", "2000")
    var taskCount: Int = 500

    /** Roughly how much CPU work each task does / 每个任务的 CPU 工作量（粗略） */
    @Param("1")
    var workTokens: Int = 1

    private lateinit var executor: ExecutorService
    private lateinit var dispatcher: CoroutineDispatcher

    @Setup(Level.Trial)
    fun setupTrial() {
        // Fixed pool approximates current scheduler's executor semantics.
        // 固定线程池：更接近当前调度器的线程池语义。
        val threads = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        executor = Executors.newFixedThreadPool(threads)
        dispatcher = executor.asCoroutineDispatcher()
    }

    @TearDown(Level.Trial)
    fun tearDownTrial() {
        executor.shutdownNow()
    }

    @Benchmark
    fun javaExecutor_submitAndJoin(bh: Blackhole) {
        val futures = ArrayList<Future<*>>(taskCount)
        repeat(taskCount) {
            futures += executor.submit {
                // Tiny payload; focuses on scheduling/join overhead.
                tinyWork(bh)
            }
        }
        for (f in futures) {
            f.get()
        }
    }

    @Benchmark
    fun coroutines_launchAndJoinAll(bh: Blackhole) {
        // runBlocking models "main thread waits for all concurrent work this tick".
        // runBlocking 模拟“主线程等待本 tick 并发工作全部完成”的 join 屏障。
        runBlocking {
            val scope = CoroutineScope(coroutineContext)
            val jobs = ArrayList<kotlinx.coroutines.Job>(taskCount)
            repeat(taskCount) {
                jobs += scope.launch(dispatcher) {
                    tinyWork(bh)
                }
            }
            jobs.joinAll()
        }
    }

    private fun tinyWork(bh: Blackhole) {
        // Avoid depending on Blackhole.consumeCPU (not available on all JMH versions).
        // 用最小的 consume 循环模拟极小工作量，保持基准聚焦“调度/join 开销”。
        val n = workTokens.coerceAtLeast(1)
        for (i in 0 until n) {
            bh.consume(i)
        }
    }
}
