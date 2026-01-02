package github.kasuminova.prototypemachinery.api.platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Platform abstraction for PrototypeMachinery.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Must be usable from Java 8 (main mod build) so that legacy environments keep working.</li>
 *   <li>Allow optional, separate "backend" mods (e.g. Cleanroom + Java 21+) to provide enhanced implementations.</li>
 *   <li>Keep the surface area small and stable; expand only when real call sites exist.</li>
 * </ul>
 */
public interface PMPlatform {

    /**
     * A short, stable identifier (e.g. {@code legacy}, {@code modern-backend}).
     */
    String id();

    /**
     * Human-readable implementation name for logging/debug.
     */
    String displayName();

    /**
     * Whether this platform implementation requires / targets a modern JVM (Java 21+).
     *
     * <p>Note: the legacy main mod does not depend on this being true/false for correctness;
     * it's primarily diagnostic and for feature gating.
     */
    boolean isModernJvmTarget();

    /**
     * Whether this platform expects to use virtual threads (Java 21+).
     */
    default boolean supportsVirtualThreads() {
        return false;
    }

    /**
     * Whether the Vector API is available at runtime.
     *
     * <p>On Java 21 this typically requires starting the JVM with:
     * {@code --add-modules jdk.incubator.vector}.
     */
    default boolean supportsVectorApi() {
        return false;
    }

    /**
     * Executor for background/scheduler work.
     *
     * <p>Default uses {@link java.util.concurrent.ForkJoinPool#commonPool()} to avoid creating
     * dedicated threads in the legacy environment.
     */
    default ExecutorService schedulerExecutor() {
        return java.util.concurrent.ForkJoinPool.commonPool();
    }

    /**
     * Create a dedicated executor for scheduler/background work.
     *
     * <p>This is preferred over {@link #schedulerExecutor()} for subsystems that own the executor
     * lifecycle and will shut it down.
     *
     * <p>Default: a fixed thread pool with daemon threads and a mild priority bump.
     */
    default ExecutorService createSchedulerExecutor(int workerThreads, String threadNamePrefix) {
        final int threads = Math.max(1, workerThreads);
        final String prefix = (threadNamePrefix == null || threadNamePrefix.trim().isEmpty())
                ? "PM-Scheduler"
                : threadNamePrefix.trim();

        final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger(0);
        return Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, prefix + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            t.setPriority(Math.min(Thread.NORM_PRIORITY + 1, Thread.MAX_PRIORITY));
            return t;
        });
    }

    /**
     * Create a dedicated single-thread executor for affinity lanes.
     *
     * <p>Default: one daemon thread.
     */
    default ExecutorService createSchedulerLaneExecutor(int laneIndex, String threadNamePrefix) {
        final int lane = Math.max(1, laneIndex);
        final String prefix = (threadNamePrefix == null || threadNamePrefix.trim().isEmpty())
                ? "PM-Scheduler"
                : threadNamePrefix.trim();

        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, prefix + "-Lane-" + lane);
            t.setDaemon(true);
            t.setPriority(Math.min(Thread.NORM_PRIORITY + 1, Thread.MAX_PRIORITY));
            return t;
        });
    }

    /**
     * Executor for render-build / mesh-build tasks.
     *
     * <p>Default reuses {@link #schedulerExecutor()}.
     */
    default java.util.concurrent.Executor renderBuildExecutor() {
        return schedulerExecutor();
    }

    /**
     * Optional Gecko vertex pipeline hook.
     *
     * <p>Default is {@code null} (legacy path).
     */
    default PMGeckoVertexPipeline geckoVertexPipeline() {
        return null;
    }

    /**
     * Optional lifecycle hook.
     */
    default void shutdown() {
        // no-op
    }
}
