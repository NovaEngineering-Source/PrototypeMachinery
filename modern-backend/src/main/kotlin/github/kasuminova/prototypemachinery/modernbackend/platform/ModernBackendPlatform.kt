package github.kasuminova.prototypemachinery.modernbackend.platform

import github.kasuminova.prototypemachinery.api.platform.PMGeckoVertexPipeline
import github.kasuminova.prototypemachinery.api.platform.PMPlatform
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ModernBackendPlatform : PMPlatform {

    // Virtual threads are cheap; we still keep one executor instance to allow controlled shutdown.
    private val scheduler: ExecutorService by lazy {
        Executors.newVirtualThreadPerTaskExecutor()
    }

    private val renderBuild: Executor by lazy {
        // For now, reuse the same VT executor; can be split later if render builds need isolation.
        scheduler
    }

    private val geckoPipeline: PMGeckoVertexPipeline by lazy(LazyThreadSafetyMode.PUBLICATION) {
        ModernBackendGeckoVertexPipeline()
    }

    override fun id(): String = "modern-backend"

    override fun displayName(): String = "Modern Backend (Cleanroom + Java 21+)"

    override fun isModernJvmTarget(): Boolean = true

    override fun supportsVirtualThreads(): Boolean = true

    override fun supportsVectorApi(): Boolean {
        // Vector API is an incubator module; it may be absent unless JVM is started with:
        // --add-modules jdk.incubator.vector
        return runCatching {
            Class.forName("jdk.incubator.vector.Vector")
            true
        }.getOrDefault(false)
    }

    override fun schedulerExecutor(): ExecutorService = scheduler

    override fun renderBuildExecutor(): Executor = renderBuild

    override fun geckoVertexPipeline(): PMGeckoVertexPipeline = geckoPipeline

    override fun createSchedulerExecutor(workerThreads: Int, threadNamePrefix: String): ExecutorService {
        // Virtual threads are cheap; ignore workerThreads and create one VT per task.
        val prefix = threadNamePrefix.ifBlank { "PM-Scheduler" }
        val factory = Thread.ofVirtual().name("$prefix-", 1).factory()
        return Executors.newThreadPerTaskExecutor(factory)
    }

    override fun createSchedulerLaneExecutor(laneIndex: Int, threadNamePrefix: String): ExecutorService {
        // Preserve lane serialization semantics by using a single (virtual) worker thread.
        val prefix = threadNamePrefix.ifBlank { "PM-Scheduler" }
        val lane = laneIndex.coerceAtLeast(1)
        val factory = Thread.ofVirtual().name("$prefix-Lane-$lane").factory()
        return Executors.newSingleThreadExecutor(factory)
    }

    override fun shutdown() {
        runCatching { scheduler.shutdown() }
    }
}
