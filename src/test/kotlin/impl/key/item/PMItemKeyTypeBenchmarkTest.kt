package github.kasuminova.prototypemachinery.impl.key.item

import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.ArrayList
import java.util.Collections
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * A simple manual benchmark to test PMItemKeyType throughput.
 * Run this via Gradle test task.
 */
@Tag("benchmark")
class PMItemKeyTypeBenchmarkTest {

    @Test
    fun runBenchmark() {
        println("Waiting 10 seconds for debugger attach (if needed)...")
        Thread.sleep(1000 * 10)

        try {
            Bootstrap.register()
        } catch (e: Exception) {
            // Ignore if already registered or fails, might not be needed for some envs
            println("Bootstrap registration failed or skipped: ${e.message}")
        }

        println("Initializing Benchmark Environment...")

        // Initialize dummy items
        val item1 = object : Item() {}
        val item2 = object : Item() {}

        // Case 1: Simple NBT
        val simpleNbt = NBTTagCompound()
        simpleNbt.setInteger("SomeKey", 123)
        simpleNbt.setString("SomeString", "BenchmarkData")
        val stackSimpleNbt = ItemStack(item1, 64, 10)
        stackSimpleNbt.tagCompound = simpleNbt

        // Case 2: No NBT
        val stackNoNbt = ItemStack(item1, 64, 5)

        // Case 3: Complex/Nested NBT
        val complexNbt = createComplexNBT(5)
        val stackComplexNbt = ItemStack(item1, 1, 0)
        stackComplexNbt.tagCompound = complexNbt

        // Case 4: Different Meta (same item, same simple NBT)
        val stackDifferentMeta = ItemStack(item1, 1, 999)
        stackDifferentMeta.tagCompound = simpleNbt.copy()

        // Case 5: Different Item (same simple NBT)
        val stackDifferentItem = ItemStack(item2, 1, 0)
        stackDifferentItem.tagCompound = simpleNbt.copy()

        // Pre-populate registry to ensure we are testing lookup speed (Hit case)
        println("Pre-populating cache...")
        PMItemKeyType.getUniqueKey(stackSimpleNbt)
        PMItemKeyType.getUniqueKey(stackNoNbt)
        PMItemKeyType.getUniqueKey(stackComplexNbt)
        PMItemKeyType.getUniqueKey(stackDifferentMeta)
        PMItemKeyType.getUniqueKey(stackDifferentItem)

        // Generate 1000 different complex NBT stacks for High Cardinality test
        val highCardinalityStacks = (0 until 1000).map { i ->
            val s = ItemStack(item1, 1, 0)
            s.tagCompound = createComplexNBT(5).apply { setInteger("UniqueId", i) }
            s
        }
        highCardinalityStacks.forEach { PMItemKeyType.getUniqueKey(it) }

        println("===================================================")
        println("Starting Benchmark: PMItemKeyType.getUniqueKey")
        println("===================================================")

        // Warmup
        println("Warmup Phase...")
        runBenchmarkCase("Warmup - Simple NBT", stackSimpleNbt, 3, 100_000)
        runBenchmarkCase("Warmup - No NBT", stackNoNbt, 3, 100_000)
        runBenchmarkCase("Warmup - Complex NBT", stackComplexNbt, 3, 100_000)

        println("\nMeasurement Phase (Single Thread)...")
        // Measurement
        runBenchmarkCase("Measure - Simple NBT", stackSimpleNbt, 5, 1_000_000)
        runBenchmarkCase("Measure - No NBT", stackNoNbt, 5, 1_000_000)
        runBenchmarkCase("Measure - Complex NBT", stackComplexNbt, 5, 1_000_000)
        runBenchmarkCase("Measure - Diff Meta", stackDifferentMeta, 5, 1_000_000)
        runBenchmarkCase("Measure - Diff Item", stackDifferentItem, 5, 1_000_000)
        runHighCardinalityBenchmark("Measure - High Cardinality Complex NBT", highCardinalityStacks, 5, 1_000_000)

        println("\nMeasurement Phase (Concurrent - 4 Threads)...")
        val cpuCount = 4
        println("Using $cpuCount threads (System CPU count)")

        runConcurrentBenchmarkCase("Concurrent - Simple NBT", stackSimpleNbt, cpuCount, 5, 1_000_000)
        runConcurrentBenchmarkCase("Concurrent - No NBT", stackNoNbt, cpuCount, 5, 1_000_000)
        runConcurrentBenchmarkCase("Concurrent - Complex NBT", stackComplexNbt, cpuCount, 5, 1_000_000)

        println("\nMeasurement Phase (High Cardinality Complex NBT)...")
        runConcurrentHighCardinalityBenchmark("Concurrent - High Cardinality Complex NBT", highCardinalityStacks, cpuCount, 5, 100_000)

        println("\nMeasurement Phase (Mixed Workload - 80% Hit / 20% Miss)...")
        // Generate 50,000 items for mixed workload
        val mixedWorkloadCount = 50_000
        val mixedStacks = ArrayList<ItemStack>(mixedWorkloadCount)
        for (i in 0 until mixedWorkloadCount) {
            val s = ItemStack(item1, 1, 0)
            // Use slightly less complex NBT (depth 3) to keep generation time reasonable
            s.tagCompound = createComplexNBT(3).apply { setInteger("UniqueId", i + 1000000) }
            mixedStacks.add(s)
        }
        // Shuffle to mix the ones we will pre-cache and the ones we won't
        Collections.shuffle(mixedStacks)

        // Pre-cache 80%
        val preCacheCount = (mixedWorkloadCount * 0.8).toInt()
        println("Pre-caching $preCacheCount items out of $mixedWorkloadCount...")
        for (i in 0 until preCacheCount) {
            PMItemKeyType.getUniqueKey(mixedStacks[i])
        }

        runMixedWorkloadBenchmark("Mixed Workload (80% Cached)", mixedStacks, 5)
    }

    private fun runMixedWorkloadBenchmark(name: String, stacks: List<ItemStack>, iterations: Int) {
        var totalTimeNs = 0L
        val opsPerIter = stacks.size

        println("Running: $name ($iterations iterations, $opsPerIter ops/iter)")

        for (i in 1..iterations) {
            val start = System.nanoTime()
            for (stack in stacks) {
                PMItemKeyType.getUniqueKey(stack)
            }
            val end = System.nanoTime()
            val took = end - start
            totalTimeNs += took

            val ms = TimeUnit.NANOSECONDS.toMillis(took)
            val opsPerMs = if (ms > 0) opsPerIter / ms else opsPerIter // Avoid div by zero
            println("  Iteration $i: $ms ms (~$opsPerMs ops/ms)")
        }

        val avgTimeNs = totalTimeNs / iterations
        val avgMs = TimeUnit.NANOSECONDS.toMillis(avgTimeNs)
        val throughput = (opsPerIter.toDouble() * 1_000_000_000.0) / (totalTimeNs.toDouble() / iterations)

        println("Result [$name]:")
        println("  Avg Time: $avgMs ms/iter")
        println("  Throughput: ${"%.2f".format(throughput)} ops/sec")
        println("---------------------------------------------------")
    }

    private fun runHighCardinalityBenchmark(name: String, stacks: List<ItemStack>, iterations: Int, opsPerIter: Int) {
        var totalTimeNs = 0L

        println("Running: $name ($iterations iterations, $opsPerIter ops/iter)")

        // Use a copy of stacks to avoid modifying the original list elements if we were paranoid,
        // but in single thread it's fine as long as we don't rely on stack state between calls.
        // getUniqueKey restores state.

        for (i in 1..iterations) {
            val start = System.nanoTime()
            val listSize = stacks.size
            for (j in 0 until opsPerIter) {
                val stack = stacks[j % listSize]
                PMItemKeyType.getUniqueKey(stack)
            }
            val end = System.nanoTime()
            val took = end - start
            totalTimeNs += took

            val ms = TimeUnit.NANOSECONDS.toMillis(took)
            val opsPerMs = (opsPerIter.toDouble() / (took.toDouble() / 1_000_000.0)).toLong()
            println("  Iteration $i: $ms ms (~$opsPerMs ops/ms)")
        }

        val avgTimeNs = totalTimeNs / iterations
        val avgMs = TimeUnit.NANOSECONDS.toMillis(avgTimeNs)
        val throughput = (opsPerIter.toDouble() * 1_000_000_000.0) / (totalTimeNs.toDouble() / iterations)

        println("Result [$name]:")
        println("  Avg Time: $avgMs ms/iter")
        println("  Throughput: ${"%.2f".format(throughput)} ops/sec")
        println("---------------------------------------------------")
    }

    private fun runConcurrentHighCardinalityBenchmark(
        name: String,
        stacks: List<ItemStack>,
        threads: Int,
        iterations: Int,
        opsPerIterPerThread: Int
    ) {
        println("Running: $name ($threads threads, $iterations iterations, $opsPerIterPerThread ops/thread/iter)")
        val executor = Executors.newFixedThreadPool(threads)
        var totalTimeNs = 0L

        // Thread local copies of the list to avoid concurrent modification of stacks inside the list
        // (since getUniqueKey modifies stack temporarily)
        val threadStackLists = ThreadLocal.withInitial {
            stacks.map { it.copy() }
        }

        for (i in 1..iterations) {
            val start = System.nanoTime()
            val tasks = (1..threads).map {
                executor.submit {
                    val localStacks = threadStackLists.get()
                    val listSize = localStacks.size
                    for (j in 0 until opsPerIterPerThread) {
                        // Round robin access
                        val stack = localStacks[j % listSize]
                        PMItemKeyType.getUniqueKey(stack)
                    }
                }
            }

            tasks.forEach { it.get() }

            val end = System.nanoTime()
            val took = end - start
            totalTimeNs += took

            val ms = TimeUnit.NANOSECONDS.toMillis(took)
            val totalOps = opsPerIterPerThread * threads
            val opsPerMs = (totalOps.toDouble() / (took.toDouble() / 1_000_000.0)).toLong()
            println("  Iteration $i: $ms ms (~$opsPerMs ops/ms total)")
        }

        executor.shutdown()

        val avgTimeNs = totalTimeNs / iterations
        val avgMs = TimeUnit.NANOSECONDS.toMillis(avgTimeNs)
        val totalOpsPerIter = opsPerIterPerThread * threads
        val throughput = (totalOpsPerIter.toDouble() * 1_000_000_000.0) / (totalTimeNs.toDouble() / iterations)

        println("Result [$name]:")
        println("  Avg Time: $avgMs ms/iter")
        println("  Throughput: ${"%.2f".format(throughput)} ops/sec (Total)")
        println("---------------------------------------------------")
    }

    private fun createComplexNBT(depth: Int): NBTTagCompound {
        val tag = NBTTagCompound()
        tag.setString("key_$depth", "value_$depth")
        tag.setInteger("int_$depth", depth)
        if (depth > 0) {
            tag.setTag("nested_a", createComplexNBT(depth - 1))
            tag.setTag("nested_b", createComplexNBT(depth - 1))
        }
        return tag
    }

    private fun runBenchmarkCase(name: String, stack: ItemStack, iterations: Int, opsPerIter: Int) {
        var totalTimeNs = 0L

        println("Running: $name ($iterations iterations, $opsPerIter ops/iter)")

        for (i in 1..iterations) {
            // Create a copy for each iteration to ensure thread safety if we were running concurrently,
            // but here it's single threaded. However, getUniqueKey modifies the stack temporarily.
            // In single thread it's fine.
            val start = System.nanoTime()
            for (j in 0 until opsPerIter) {
                PMItemKeyType.getUniqueKey(stack)
            }
            val end = System.nanoTime()
            val took = end - start
            totalTimeNs += took

            val ms = TimeUnit.NANOSECONDS.toMillis(took)
            val opsPerMs = (opsPerIter.toDouble() / (took.toDouble() / 1_000_000.0)).toLong()
            println("  Iteration $i: $ms ms (~$opsPerMs ops/ms)")
        }

        val avgTimeNs = totalTimeNs / iterations
        val avgMs = TimeUnit.NANOSECONDS.toMillis(avgTimeNs)
        val throughput = (opsPerIter.toDouble() * 1_000_000_000.0) / (totalTimeNs.toDouble() / iterations)

        println("Result [$name]:")
        println("  Avg Time: $avgMs ms/iter")
        println("  Throughput: ${"%.2f".format(throughput)} ops/sec")
        println("---------------------------------------------------")
    }

    private fun runConcurrentBenchmarkCase(name: String, stack: ItemStack, threads: Int, iterations: Int, opsPerIterPerThread: Int) {
        println("Running: $name ($threads threads, $iterations iterations, $opsPerIterPerThread ops/thread/iter)")
        val executor = Executors.newFixedThreadPool(threads)
        var totalTimeNs = 0L

        // We need separate stacks for each thread because getUniqueKey modifies the stack in-place temporarily.
        // Although it restores it, concurrent modification of the SAME stack instance is NOT safe.
        // The benchmark must simulate multiple threads accessing the cache with *different* stack instances
        // (even if they represent the same item) or ensure thread-local stacks.
        val threadStacks = ThreadLocal.withInitial { stack.copy() }

        for (i in 1..iterations) {
            val start = System.nanoTime()
            val tasks = (1..threads).map {
                executor.submit {
                    val localStack = threadStacks.get()
                    for (j in 0 until opsPerIterPerThread) {
                        PMItemKeyType.getUniqueKey(localStack)
                    }
                }
            }

            // Wait for all to finish
            tasks.forEach { it.get() }

            val end = System.nanoTime()
            val took = end - start
            totalTimeNs += took

            val ms = TimeUnit.NANOSECONDS.toMillis(took)
            val totalOps = opsPerIterPerThread * threads
            val opsPerMs = (totalOps.toDouble() / (took.toDouble() / 1_000_000.0)).toLong()
            println("  Iteration $i: $ms ms (~$opsPerMs ops/ms total)")
        }

        executor.shutdown()

        val avgTimeNs = totalTimeNs / iterations
        val avgMs = TimeUnit.NANOSECONDS.toMillis(avgTimeNs)
        val totalOpsPerIter = opsPerIterPerThread * threads
        val throughput = (totalOpsPerIter.toDouble() * 1_000_000_000.0) / (totalTimeNs.toDouble() / iterations)

        println("Result [$name]:")
        println("  Avg Time: $avgMs ms/iter")
        println("  Throughput: ${"%.2f".format(throughput)} ops/sec (Total)")
        println("---------------------------------------------------")
    }
}
