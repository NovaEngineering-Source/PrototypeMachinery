package github.kasuminova.prototypemachinery.impl.storage

import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyImpl
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import net.minecraft.init.Bootstrap
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import java.util.Random
import java.util.concurrent.TimeUnit

/**
 * A simple manual benchmark to test [ItemResourceStorage] hot paths.
 *
 * Runs under JUnit with @Tag("benchmark").
 * 默认 `test` 任务会排除该 tag；用 `./gradlew benchmark` 运行。
 */
@Tag("benchmark")
class ItemResourceStorageBenchmarkTest {

    @Test
    fun runBenchmark() {
        try {
            Bootstrap.register()
        } catch (e: Exception) {
            println("Bootstrap registration failed or skipped: ${e.message}")
        }

        // Tune these locally if needed
        val maxTypes = 4096
        val uniqueTypes = 512
        val maxCountPerType = 100_000L

        println("===================================================")
        println("Starting Benchmark: ItemResourceStorage (Map + virtual slots)")
        println("maxTypes=$maxTypes, uniqueTypes=$uniqueTypes, maxCountPerType=$maxCountPerType")
        println("===================================================")

        val (storage, keys, occupiedSlots) = createPrefilledStorage(maxTypes, uniqueTypes, maxCountPerType)

        // Warmup
        println("Warmup Phase...")
        runTimedCase("Warmup - insert(hit) simulate=false", iterations = 2, opsPerIter = 100_000) {
            benchmarkInsertHit(storage, keys, ops = 100_000, simulate = false)
        }
        runTimedCase("Warmup - extractFromSlot(random)", iterations = 2, opsPerIter = 100_000) {
            benchmarkExtractFromSlot(storage, occupiedSlots, ops = 100_000)
        }
        runTimedCase("Warmup - getSlot(full-scan)", iterations = 2, opsPerIter = maxTypes) {
            benchmarkFullScan(storage, maxTypes)
        }

        // Measurement
        println("\nMeasurement Phase...")
        runTimedCase("Measure - insert(hit) simulate=true", iterations = 5, opsPerIter = 500_000) {
            benchmarkInsertHit(storage, keys, ops = 500_000, simulate = true)
        }
        runTimedCase("Measure - insert(hit) simulate=false (+dirty tracking)", iterations = 5, opsPerIter = 200_000) {
            benchmarkInsertHit(storage, keys, ops = 200_000, simulate = false)
            // include cost of draining dirty indices
            storage.drainPendingSlotChanges()
            storage.clearPendingChanges()
        }
        runTimedCase("Measure - extractFromSlot(random)", iterations = 5, opsPerIter = 200_000) {
            benchmarkExtractFromSlot(storage, occupiedSlots, ops = 200_000)
            storage.drainPendingSlotChanges()
            storage.clearPendingChanges()
        }
        runTimedCase("Measure - drainPendingSlotChanges after mutations", iterations = 5, opsPerIter = 200_000) {
            benchmarkDrainAfterMutations(storage, keys, ops = 200_000)
        }
        runTimedCase("Measure - getSlot(full-scan)", iterations = 5, opsPerIter = maxTypes) {
            benchmarkFullScan(storage, maxTypes)
        }

        // Scenario per request:
        // - total slots = 100
        // - pre-insert 50 unique types
        // - then run frequent get/insert/extract operations with ~75 unique types involved
        runScenario_100Slots_50Prefill_75Active(maxCountPerType)

        println("Done.")
    }

    private fun runScenario_100Slots_50Prefill_75Active(maxCountPerType: Long) {
        val maxTypes = 100
        val prefillTypes = 50
        val activeTypes = 75

        println("\n===================================================")
        println("Scenario: 100 slots, 50 prefilled types, ~75 active types")
        println("maxTypes=$maxTypes, prefillTypes=$prefillTypes, activeTypes=$activeTypes, maxCountPerType=$maxCountPerType")
        println("===================================================")

        val (storage, activeKeys) = createScenarioStorage(
            maxTypes = maxTypes,
            prefillTypes = prefillTypes,
            activeTypes = activeTypes,
            maxCountPerType = maxCountPerType
        )

        // Warmup
        runTimedCase("Scenario Warmup - mixed workload", iterations = 2, opsPerIter = 200_000) {
            benchmarkMixedWorkload(storage, activeKeys, maxTypes, ops = 200_000)
        }

        // Measurement
        runTimedCase("Scenario Measure - mixed workload", iterations = 5, opsPerIter = 500_000) {
            benchmarkMixedWorkload(storage, activeKeys, maxTypes, ops = 500_000)
        }
    }

    private fun createPrefilledStorage(
        maxTypes: Int,
        uniqueTypes: Int,
        maxCountPerType: Long
    ): Triple<ItemResourceStorage, Array<PMItemKeyImpl>, IntArray> {
        val storage = ItemResourceStorage(maxTypes, maxCountPerType)

        val keys = Array(uniqueTypes) { i ->
            val item = object : Item() {}

            // Add a tiny NBT so UniquePMItemKey hashing covers both paths (with/without NBT).
            // 但不要在 benchmark 循环里创建 NBT，避免把测量变成“key 构建”测试。
            val tag = if ((i and 1) == 0) {
                null
            } else {
                NBTTagCompound().apply {
                    setInteger("k", i)
                    setString("s", "v$i")
                }
            }

            val stack = ItemStack(item, 1, 0)
            stack.tagCompound = tag
            val unique = PMItemKeyType.getUniqueKey(stack)
            PMItemKeyImpl(unique, 1L)
        }

        // Prefill: make each key occupy ~2 virtual slots (one full + half)
        val prefill = maxCountPerType + (maxCountPerType / 2)
        for (k in keys) {
            storage.insert(k, prefill, false)
        }

        // Build occupied slot list
        val occupied = IntArray(maxTypes)
        var count = 0
        for (i in 0 until maxTypes) {
            if (storage.getSlot(i) != null) occupied[count++] = i
        }

        storage.drainPendingSlotChanges()
        storage.clearPendingChanges()

        return Triple(storage, keys, occupied.copyOf(count))
    }

    /**
     * Builds the requested scenario:
     * - maxTypes slots total
     * - first [prefillTypes] keys are inserted with enough amount to occupy ~2 slots each
     *   (so 50 types roughly fill 100 slots)
     * - [activeTypes] keys are used for the mixed workload (so ~25 extra types may churn in/out)
     */
    private fun createScenarioStorage(
        maxTypes: Int,
        prefillTypes: Int,
        activeTypes: Int,
        maxCountPerType: Long
    ): Pair<ItemResourceStorage, Array<PMItemKeyImpl>> {
        require(prefillTypes in 1..activeTypes) { "prefillTypes must be within 1..activeTypes" }

        val storage = ItemResourceStorage(maxTypes, maxCountPerType)

        val keys = Array(activeTypes) { i ->
            val item = object : Item() {}

            // Keep key generation cost out of the measurement loop.
            // Mix NBT/no-NBT to cover both UniquePMItemKey paths.
            val tag = if ((i and 1) == 0) {
                null
            } else {
                NBTTagCompound().apply {
                    setInteger("k", i)
                    setString("s", "v$i")
                }
            }

            val stack = ItemStack(item, 1, 0)
            stack.tagCompound = tag
            val unique = PMItemKeyType.getUniqueKey(stack)
            PMItemKeyImpl(unique, 1L)
        }

        // Prefill each of first N types with ~1.5 slotCap amount -> should occupy 2 slots.
        val prefillAmount = maxCountPerType + (maxCountPerType / 2)
        for (i in 0 until prefillTypes) {
            storage.insert(keys[i], prefillAmount, false)
        }

        storage.drainPendingSlotChanges()
        storage.clearPendingChanges()

        return storage to keys
    }

    private fun benchmarkInsertHit(storage: ItemResourceStorage, keys: Array<PMItemKeyImpl>, ops: Int, simulate: Boolean): Long {
        var sum = 0L
        // deterministic key selection, avoid Random cost
        for (i in 0 until ops) {
            val k = keys[i % keys.size]
            sum += storage.insert(k, 64L, simulate)
        }
        return sum
    }

    private fun benchmarkExtractFromSlot(storage: ItemResourceStorage, occupiedSlots: IntArray, ops: Int): Long {
        if (occupiedSlots.isEmpty()) return 0L
        val rnd = Random(12345)
        var sum = 0L
        for (i in 0 until ops) {
            val slot = occupiedSlots[rnd.nextInt(occupiedSlots.size)]
            sum += storage.extractFromSlot(slot, 32L, false)
        }
        return sum
    }

    private fun benchmarkDrainAfterMutations(storage: ItemResourceStorage, keys: Array<PMItemKeyImpl>, ops: Int): Int {
        for (i in 0 until ops) {
            val k = keys[i % keys.size]
            storage.insert(k, 1L, false)
        }
        val dirty = storage.drainPendingSlotChanges()
        storage.clearPendingChanges()
        return dirty.size
    }

    private fun benchmarkFullScan(storage: ItemResourceStorage, maxTypes: Int): Long {
        var sum = 0L
        for (i in 0 until maxTypes) {
            val k = storage.getSlot(i)
            if (k != null) sum += k.count
        }
        return sum
    }

    /**
     * Mixed workload that combines:
     * - frequent getSlot (random slot)
     * - insert by key
     * - extract by key
     *
     * 期间约 [keys.size] 种物品会参与测试（包含一部分未预先插入的类型，用于 churn）。
     */
    private fun benchmarkMixedWorkload(
        storage: ItemResourceStorage,
        keys: Array<PMItemKeyImpl>,
        maxTypes: Int,
        ops: Int
    ): Long {
        val rnd = Random(12345)
        var sink = 0L

        // Keep constants outside loop
        val smallInsert = 64L
        val smallExtract = 32L
        val bigExtract = storage.maxCountPerType * 2

        for (i in 0 until ops) {
            val r = rnd.nextInt(100)
            when {
                // 60%: frequent get
                r < 60 -> {
                    val slot = rnd.nextInt(maxTypes)
                    val k = storage.getSlot(slot)
                    // touch fields to avoid DCE
                    sink += (k?.count ?: 0L)
                }

                // 20%: insert
                r < 80 -> {
                    val key = keys[rnd.nextInt(keys.size)]
                    sink += storage.insert(key, smallInsert, false)
                }

                // 20%: extract (sometimes big to force type eviction and free slots)
                else -> {
                    val key = keys[rnd.nextInt(keys.size)]
                    val amount = if ((i and 0xFF) == 0) bigExtract else smallExtract
                    sink += storage.extract(key, amount, false)
                }
            }
        }

        // include dirty-queue maintenance overhead
        val dirty = storage.drainPendingSlotChanges()
        sink += dirty.size.toLong()
        storage.clearPendingChanges()

        return sink
    }

    private fun runTimedCase(name: String, iterations: Int, opsPerIter: Int, block: () -> Any) {
        var totalNs = 0L
        var sink: Any? = null

        println("Running: $name ($iterations iterations, $opsPerIter ops/iter)")
        for (i in 1..iterations) {
            val start = System.nanoTime()
            sink = block()
            val end = System.nanoTime()
            val took = end - start
            totalNs += took

            val ms = TimeUnit.NANOSECONDS.toMillis(took)
            val throughput = (opsPerIter.toDouble() * 1_000_000_000.0) / took.toDouble()
            println("  Iteration $i: $ms ms (%.2f ops/sec)".format(throughput))
        }

        val avgNs = totalNs / iterations
        val avgThroughput = (opsPerIter.toDouble() * 1_000_000_000.0) / avgNs.toDouble()
        println("Result [$name]: avg %.2f ms/iter, avg %.2f ops/sec, sink=$sink".format(
            avgNs.toDouble() / 1_000_000.0,
            avgThroughput
        ))
        println("---------------------------------------------------")
    }
}
