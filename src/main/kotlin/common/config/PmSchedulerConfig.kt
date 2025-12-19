package github.kasuminova.prototypemachinery.common.config

import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraftforge.common.config.Config

/**
 * Forge @Config for TaskScheduler backend selection and tuning.
 *
 * 配置文件位置：config/${PrototypeMachinery.MOD_ID}.cfg
 */
@Config(modid = PrototypeMachinery.MOD_ID)
public object PmSchedulerConfig {

    @JvmField
    public var scheduler: Scheduler = Scheduler()

    public class Scheduler {
        /**
         * Scheduler backend type.
         * 可选：JAVA / COROUTINES
         */
        @JvmField
        public var backend: String = "JAVA"

        /**
         * Worker thread count for concurrent tasks (non-affinity).
         */
        @JvmField
        public var workerThreads: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

        /**
         * Lane count for affinity groups.
         *
         * Important: this is a FIXED upper bound of threads used for affinity serialization.
         * 重要：这里是 affinity 串行通道的固定线程上限，避免因 key 数量过多创建大量线程。
         */
        @JvmField
        public var laneCount: Int = Runtime.getRuntime().availableProcessors().coerceIn(2, 8)

        /**
         * Enable periodic performance log.
         */
        @JvmField
        public var metricsEnabled: Boolean = false

        /**
         * How often (in ticks) to print a summary to log.
         */
        @JvmField
        public var metricsLogIntervalTicks: Int = 200

        /**
         * Sliding window size (in ticks) used for report/percentiles.
         */
        @JvmField
        public var metricsWindowTicks: Int = 400
    }
}
