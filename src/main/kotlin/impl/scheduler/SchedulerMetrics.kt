package github.kasuminova.prototypemachinery.impl.scheduler

import github.kasuminova.prototypemachinery.PrototypeMachinery
import java.util.concurrent.atomic.AtomicLong

internal class SchedulerMetrics(
    windowTicks: Int,
) {
    @Volatile
    private var windowSize: Int = windowTicks

    private var cursor: Int = 0
    private var filled: Boolean = false

    private var backendName: String = ""

    private var mainPhaseMicros = LongArray(windowTicks)
    private var concurrentPhaseMicros = LongArray(windowTicks)
    private var joinMicros = LongArray(windowTicks)
    private var totalTickMicros = LongArray(windowTicks)

    private var mainTasksCount = IntArray(windowTicks)
    private var concurrentTasksCount = IntArray(windowTicks)
    private var customMainTasksCount = IntArray(windowTicks)
    private var customConcurrentTasksCount = IntArray(windowTicks)
    private var affinityGroupsCount = IntArray(windowTicks)

    private val ticks = AtomicLong(0)

    fun resizeIfNeeded(newWindowTicks: Int) {
        val newSize = newWindowTicks
        if (newSize == windowSize) return

        windowSize = newSize
        cursor = 0
        filled = false

        mainPhaseMicros = LongArray(newSize)
        concurrentPhaseMicros = LongArray(newSize)
        joinMicros = LongArray(newSize)
        totalTickMicros = LongArray(newSize)
        mainTasksCount = IntArray(newSize)
        concurrentTasksCount = IntArray(newSize)
        customMainTasksCount = IntArray(newSize)
        customConcurrentTasksCount = IntArray(newSize)
        affinityGroupsCount = IntArray(newSize)
    }

    fun beginTick(backendName: String) {
        this.backendName = backendName
    }

    fun record(
        mainPhaseMicros: Long,
        concurrentPhaseMicros: Long,
        joinMicros: Long,
        totalTickMicros: Long,
        mainTasks: Int,
        concurrentTasks: Int,
        customMainTasks: Int,
        customConcurrentTasks: Int,
        affinityGroups: Int,
    ) {
        val i = cursor

        this.mainPhaseMicros[i] = mainPhaseMicros
        this.concurrentPhaseMicros[i] = concurrentPhaseMicros
        this.joinMicros[i] = joinMicros
        this.totalTickMicros[i] = totalTickMicros
        this.mainTasksCount[i] = mainTasks
        this.concurrentTasksCount[i] = concurrentTasks
        this.customMainTasksCount[i] = customMainTasks
        this.customConcurrentTasksCount[i] = customConcurrentTasks
        this.affinityGroupsCount[i] = affinityGroups

        cursor++
        if (cursor >= windowSize) {
            cursor = 0
            filled = true
        }
        ticks.incrementAndGet()
    }

    fun maybeLog(intervalTicks: Int, enabled: Boolean) {
        if (!enabled) return
        val t = ticks.get()
        if (t == 0L) return
        if (t % intervalTicks.toLong() != 0L) return

        val report = snapshotReport()
        PrototypeMachinery.logger.info(report.toLogLine())
    }

    fun snapshotReport(): SchedulerReport {
        val n = if (filled) windowSize else cursor
        if (n <= 0) {
            return SchedulerReport(backendName, 0, emptyList())
        }

        val samples = ArrayList<SchedulerTickSample>(n)
        for (idx in 0 until n) {
            samples.add(
                SchedulerTickSample(
                    mainPhaseMicros = mainPhaseMicros[idx],
                    concurrentPhaseMicros = concurrentPhaseMicros[idx],
                    joinMicros = joinMicros[idx],
                    totalTickMicros = totalTickMicros[idx],
                    mainTasks = mainTasksCount[idx],
                    concurrentTasks = concurrentTasksCount[idx],
                    customMainTasks = customMainTasksCount[idx],
                    customConcurrentTasks = customConcurrentTasksCount[idx],
                    affinityGroups = affinityGroupsCount[idx],
                )
            )
        }
        return SchedulerReport(backendName, n, samples)
    }
}

internal data class SchedulerTickSample(
    val mainPhaseMicros: Long,
    val concurrentPhaseMicros: Long,
    val joinMicros: Long,
    val totalTickMicros: Long,
    val mainTasks: Int,
    val concurrentTasks: Int,
    val customMainTasks: Int,
    val customConcurrentTasks: Int,
    val affinityGroups: Int,
)

internal data class SchedulerReport(
    val backendName: String,
    val sampleCount: Int,
    val samples: List<SchedulerTickSample>,
) {
    private fun statsOf(selector: (SchedulerTickSample) -> Long): StatLine {
        if (samples.isEmpty()) return StatLine(0, 0, 0)
        val arr = LongArray(samples.size) { i -> selector(samples[i]) }
        arr.sort()
        val avg = arr.sum() / arr.size
        val p95 = arr[(arr.size * 95) / 100]
        val max = arr[arr.size - 1]
        return StatLine(avg, p95, max)
    }

    private fun statsOfInt(selector: (SchedulerTickSample) -> Int): StatLine {
        if (samples.isEmpty()) return StatLine(0, 0, 0)
        val arr = IntArray(samples.size) { i -> selector(samples[i]) }
        java.util.Arrays.sort(arr)
        val avg = arr.sum().toLong() / arr.size
        val p95 = arr[(arr.size * 95) / 100].toLong()
        val max = arr[arr.size - 1].toLong()
        return StatLine(avg, p95, max)
    }

    fun toLogLine(): String {
        val total = statsOf { it.totalTickMicros }
        val main = statsOf { it.mainPhaseMicros }
        val conc = statsOf { it.concurrentPhaseMicros }
        val join = statsOf { it.joinMicros }

        val mainTasks = statsOfInt { it.mainTasks }
        val concTasks = statsOfInt { it.concurrentTasks }
        val cm = statsOfInt { it.customMainTasks }
        val cc = statsOfInt { it.customConcurrentTasks }
        val groups = statsOfInt { it.affinityGroups }

        return buildString {
            append("[PM Scheduler] backend=").append(backendName)
            append(" window=").append(sampleCount).append("t")
            append(" total(avg/p95/max)=").append(total.formatMicros())
            append(" main=").append(main.formatMicros())
            append(" concurrent=").append(conc.formatMicros())
            append(" join=").append(join.formatMicros())
            append(" tasks(main/concurrent/customM/customC)=")
            append(mainTasks.formatPlain()).append('/')
            append(concTasks.formatPlain()).append('/')
            append(cm.formatPlain()).append('/')
            append(cc.formatPlain())
            append(" groups=").append(groups.formatPlain())
        }
    }
}

internal data class StatLine(
    val avg: Long,
    val p95: Long,
    val max: Long,
) {
    fun formatMicros(): String = "${avg}µs/${p95}µs/${max}µs"
    fun formatPlain(): String = "$avg/$p95/$max"
}
