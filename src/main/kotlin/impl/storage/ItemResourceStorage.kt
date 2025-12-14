package github.kasuminova.prototypemachinery.impl.storage

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.storage.ChangeType
import github.kasuminova.prototypemachinery.api.storage.ResourceChange
import github.kasuminova.prototypemachinery.api.storage.ResourceStorageListener
import github.kasuminova.prototypemachinery.api.storage.SlottedResourceStorage
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyImpl
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.key.item.UniquePMItemKey
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.nbt.NBTTagList
import net.minecraftforge.common.util.Constants
import java.util.BitSet
import java.util.concurrent.CopyOnWriteArrayList

/**
 * # ItemResourceStorage - Map-backed slotted view
 * # ItemResourceStorage - Map 内核 + 槽位视图
 *
 * Internal representation stays aggregated (Map<UniqueKey, Long>) for performance,
 * while exposing a fixed-slot view externally.
 *
 * 内部保持按类型聚合计数（Map<UniqueKey, Long>）以优化性能，
 * 对外提供固定槽位数的“虚拟槽位”视图。
 */
public class ItemResourceStorage(
    override val maxTypes: Int,
    override val maxCountPerType: Long = Long.MAX_VALUE
) : SlottedResourceStorage<PMKey<ItemStack>> {

    private companion object {
        /** Hard cap for external ItemStack.count: Int.MAX_VALUE / 2. */
        private const val EXTERNAL_STACK_CAP: Long = (Int.MAX_VALUE / 2).toLong()
    }

    /** Per-slot capacity (also used for virtual segmentation). */
    private val slotCap: Long = maxCountPerType.coerceAtLeast(1L).coerceAtMost(EXTERNAL_STACK_CAP)

    /** Aggregated totals by unique key. */
    private val totals: MutableMap<UniquePMItemKey, Long> = LinkedHashMap()

    /** Slots assigned to a unique key, in stable order (partIndex = order index). */
    private val keySlots: MutableMap<UniquePMItemKey, MutableList<Int>> = LinkedHashMap()

    /** Slot -> unique key mapping (null if free). */
    private val slotUniqueKeys: Array<UniquePMItemKey?> = arrayOfNulls(maxTypes)

    /** Slot -> part index within that key (only meaningful when occupied). */
    private val slotPartIndex: IntArray = IntArray(maxTypes)

    /** Cached PMKey per slot (count is the per-slot amount). */
    private val slotCache: Array<PMItemKeyImpl?> = arrayOfNulls(maxTypes)

    /** Free slot index set. */
    private val freeSlots: BitSet = BitSet(maxTypes).apply { set(0, maxTypes) }

    private var usedSlotCount: Int = 0

    private val listeners: MutableList<ResourceStorageListener<PMKey<ItemStack>>> = CopyOnWriteArrayList()

    @Volatile
    private var pendingChanges: Boolean = false

    /** Pending dirty slot indices. */
    private val pendingSlotChanges: IntArrayList = IntArrayList()
    private val pendingSlotFlags: BooleanArray = BooleanArray(maxTypes)

    override val slotCount: Int
        get() = maxTypes

    /** Occupied slot count (used in UI as "slots used"). */
    override val usedTypes: Int
        get() = usedSlotCount

    override fun getSlot(index: Int): PMKey<ItemStack>? {
        if (index !in 0 until maxTypes) return null
        return slotCache[index]
    }

    override fun drainPendingSlotChanges(): IntArray {
        val result = pendingSlotChanges.toIntArray()
        for (i in result.indices) {
            val slot = result[i]
            if (slot in 0 until maxTypes) pendingSlotFlags[slot] = false
        }
        pendingSlotChanges.clear()
        return result
    }

    private fun markSlotDirty(index: Int) {
        if (index !in 0 until maxTypes) return
        if (pendingSlotFlags[index]) return
        pendingSlotFlags[index] = true
        pendingSlotChanges.add(index)
        pendingChanges = true
    }

    private fun getUniqueKeyOf(key: PMKey<ItemStack>): UniquePMItemKey {
        return (key as PMItemKey).uniqueKey
    }

    override fun getAmount(key: PMKey<ItemStack>): Long {
        return totals[getUniqueKeyOf(key)] ?: 0L
    }

    override fun contains(key: PMKey<ItemStack>): Boolean {
        return getAmount(key) > 0L
    }

    private fun requiredSlotsForTotal(total: Long): Int {
        if (total <= 0L) return 0
        return ((total + slotCap - 1L) / slotCap).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun amountForPart(total: Long, partIndex: Int): Long {
        if (total <= 0L) return 0L
        if (partIndex < 0) return 0L
        val start = partIndex.toLong() * slotCap
        val remaining = total - start
        if (remaining <= 0L) return 0L
        return minOf(remaining, slotCap)
    }

    private fun allocateFreeSlot(): Int {
        val idx = freeSlots.nextSetBit(0)
        if (idx !in 0..<maxTypes) return -1
        freeSlots.clear(idx)
        usedSlotCount++
        return idx
    }

    private fun freeSlot(index: Int) {
        if (index !in 0 until maxTypes) return
        if (slotUniqueKeys[index] == null) return
        slotUniqueKeys[index] = null
        slotCache[index] = null
        slotPartIndex[index] = 0
        freeSlots.set(index)
        usedSlotCount--
        markSlotDirty(index)
    }

    private fun ensureKeySlots(uniqueKey: UniquePMItemKey, desiredSlots: Int) {
        val list = keySlots.getOrPut(uniqueKey) { mutableListOf() }

        // Grow
        while (list.size < desiredSlots) {
            val slot = allocateFreeSlot()
            if (slot < 0) break
            val partIndex = list.size
            list.add(slot)
            slotUniqueKeys[slot] = uniqueKey
            slotPartIndex[slot] = partIndex
            slotCache[slot] = PMItemKeyImpl(uniqueKey, 0L)
            markSlotDirty(slot)
        }

        // Shrink (from end)
        while (list.size > desiredSlots) {
            val slot = list.removeAt(list.size - 1)
            freeSlot(slot)
        }

        if (list.isEmpty()) {
            keySlots.remove(uniqueKey)
        }
    }

    private fun refreshKeySlotCounts(uniqueKey: UniquePMItemKey, total: Long) {
        val list = keySlots[uniqueKey] ?: return
        for (partIndex in 0 until list.size) {
            val slot = list[partIndex]
            val key = slotCache[slot] ?: continue
            val newCount = amountForPart(total, partIndex)
            if (key.count != newCount) {
                key.count = newCount
                markSlotDirty(slot)
            }
        }
    }

    private fun availableCapacityForKey(uniqueKey: UniquePMItemKey, currentTotal: Long): Long {
        val currentSlots = keySlots[uniqueKey]?.size ?: 0
        val free = maxTypes - usedSlotCount
        val maxSlotsForThisKey = currentSlots + free
        val capacity = maxSlotsForThisKey.toLong() * slotCap
        return (capacity - currentTotal).coerceAtLeast(0L)
    }

    override fun insert(key: PMKey<ItemStack>, amount: Long, simulate: Boolean): Long {
        if (amount <= 0L) return 0L
        val uniqueKey = getUniqueKeyOf(key)

        val previousTotal = totals[uniqueKey] ?: 0L
        val canInsert = minOf(amount, availableCapacityForKey(uniqueKey, previousTotal))
        if (canInsert <= 0L) return 0L
        if (simulate) return canInsert

        val newTotal = previousTotal + canInsert
        totals[uniqueKey] = newTotal

        val neededSlots = requiredSlotsForTotal(newTotal)
        ensureKeySlots(uniqueKey, neededSlots)
        refreshKeySlotCounts(uniqueKey, newTotal)

        notifyChange(
            ResourceChange(
                key = key.copy().apply { count = newTotal },
                previousAmount = previousTotal,
                newAmount = newTotal,
                type = ChangeType.INSERTED
            )
        )

        return canInsert
    }

    override fun extract(key: PMKey<ItemStack>, amount: Long, simulate: Boolean): Long {
        if (amount <= 0L) return 0L
        val uniqueKey = getUniqueKeyOf(key)
        val previousTotal = totals[uniqueKey] ?: 0L
        if (previousTotal <= 0L) return 0L

        val extracted = minOf(amount, previousTotal)
        if (simulate) return extracted

        val newTotal = previousTotal - extracted
        if (newTotal <= 0L) {
            totals.remove(uniqueKey)
            ensureKeySlots(uniqueKey, 0)
        } else {
            totals[uniqueKey] = newTotal
            val neededSlots = requiredSlotsForTotal(newTotal)
            ensureKeySlots(uniqueKey, neededSlots)
            refreshKeySlotCounts(uniqueKey, newTotal)
        }

        notifyChange(
            ResourceChange(
                key = (key.copy() as PMKey<ItemStack>).apply { count = newTotal },
                previousAmount = previousTotal,
                newAmount = newTotal,
                type = if (newTotal <= 0L) ChangeType.REMOVED else ChangeType.EXTRACTED
            )
        )

        return extracted
    }

    override fun extractFromSlot(index: Int, amount: Long, simulate: Boolean): Long {
        if (amount <= 0L) return 0L
        if (index !in 0 until maxTypes) return 0L

        val keyInSlot = slotCache[index] ?: return 0L
        val uniqueKey = keyInSlot.uniqueKey
        val previousTotal = totals[uniqueKey] ?: 0L
        if (previousTotal <= 0L) return 0L

        val availableInSlot = keyInSlot.count
        if (availableInSlot <= 0L) return 0L

        val extracted = minOf(amount, availableInSlot)
        if (simulate) return extracted

        val newTotal = (previousTotal - extracted).coerceAtLeast(0L)
        if (newTotal <= 0L) {
            totals.remove(uniqueKey)
            ensureKeySlots(uniqueKey, 0)
        } else {
            totals[uniqueKey] = newTotal
            val neededSlots = requiredSlotsForTotal(newTotal)
            ensureKeySlots(uniqueKey, neededSlots)
            refreshKeySlotCounts(uniqueKey, newTotal)
        }

        notifyChange(
            ResourceChange(
                key = PMItemKeyImpl(uniqueKey, newTotal),
                previousAmount = previousTotal,
                newAmount = newTotal,
                type = if (newTotal <= 0L) ChangeType.REMOVED else ChangeType.EXTRACTED
            )
        )

        return extracted
    }

    /** Convenience method to insert an ItemStack. / 便捷方法：插入 ItemStack。 */
    public fun insertStack(stack: ItemStack, simulate: Boolean): Long {
        if (stack.isEmpty) return 0L
        val key = PMItemKeyType.create(stack)
        return insert(key, stack.count.toLong(), simulate)
    }

    /** Convenience method to extract an ItemStack (by type). / 便捷方法：按类型提取 ItemStack。 */
    public fun extractStack(stack: ItemStack, simulate: Boolean): Long {
        if (stack.isEmpty) return 0L
        val key = PMItemKeyType.create(stack)
        return extract(key, stack.count.toLong(), simulate)
    }

    /**
     * Extracts a specific amount of items matching the stack (by type).
     * Returns the extracted ItemStack.
     */
    public fun extractStackResult(stack: ItemStack, maxAmount: Int, simulate: Boolean): ItemStack {
        if (stack.isEmpty) return ItemStack.EMPTY
        val key = PMItemKeyType.create(stack)
        val extracted = extract(key, maxAmount.toLong(), simulate)
        if (extracted <= 0L) return ItemStack.EMPTY
        val result = stack.copy()
        result.count = extracted.coerceAtMost(EXTERNAL_STACK_CAP).toInt()
        return result
    }

    /**
     * Gets the first slot resource by its ItemStack representation.
     * 通过 ItemStack 表示获取第一个匹配槽位资源。
     */
    public fun getByStack(stack: ItemStack): PMKey<ItemStack>? {
        if (stack.isEmpty) return null
        val uniqueKey = PMItemKeyType.getUniqueKey(stack)
        val slots = keySlots[uniqueKey] ?: return null
        if (slots.isEmpty()) return null
        val first = slots[0]
        return slotCache[first]
    }

    override fun getAllResources(): Collection<PMKey<ItemStack>> {
        return slotCache.filterNotNull().toList()
    }

    override fun clear() {
        if (usedSlotCount <= 0 && totals.isEmpty()) return

        // notify per key total
        val snapshot: Map<UniquePMItemKey, Long> = HashMap(totals)

        totals.clear()
        keySlots.clear()
        for (i in 0 until maxTypes) {
            if (slotUniqueKeys[i] != null) {
                slotUniqueKeys[i] = null
                slotCache[i] = null
                slotPartIndex[i] = 0
                freeSlots.set(i)
                markSlotDirty(i)
            }
        }
        usedSlotCount = 0

        snapshot.forEach { (u, total) ->
            notifyChange(
                ResourceChange(
                    key = PMItemKeyImpl(u, 0L),
                    previousAmount = total,
                    newAmount = 0L,
                    type = ChangeType.CLEARED
                )
            )
        }
    }

    override fun addListener(listener: ResourceStorageListener<PMKey<ItemStack>>) {
        listeners.add(listener)
    }

    override fun removeListener(listener: ResourceStorageListener<PMKey<ItemStack>>): Boolean {
        return listeners.remove(listener)
    }

    override fun hasPendingChanges(): Boolean = pendingChanges

    override fun clearPendingChanges() {
        pendingChanges = false
        // keep slot flags cleared by drainPendingSlotChanges(); if caller only clears changes,
        // ensure we don't hold stale flags.
        pendingSlotChanges.clear()
        for (i in pendingSlotFlags.indices) {
            pendingSlotFlags[i] = false
        }
    }

    private fun notifyChange(change: ResourceChange<PMKey<ItemStack>>) {
        listeners.forEach { listener ->
            runCatching { listener.onResourceChanged(change) }
        }
    }

    /** Writes this storage to NBT (per-slot snapshot). / 将此存储写入 NBT（按槽位快照）。 */
    public fun writeNBT(nbt: NBTTagCompound): NBTTagCompound {
        val list = NBTTagList()
        for (i in 0 until maxTypes) {
            val key = slotCache[i] ?: continue
            if (key.count <= 0L) continue
            val keyNbt = NBTTagCompound()
            keyNbt.setInteger("Slot", i)
            key.writeNBT(keyNbt)
            list.appendTag(keyNbt)
        }
        nbt.setTag("Resources", list)
        return nbt
    }

    /** Reads this storage from NBT. / 从 NBT 读取此存储。 */
    public fun readNBT(nbt: NBTTagCompound) {
        // reset
        totals.clear()
        keySlots.clear()
        freeSlots.set(0, maxTypes)
        usedSlotCount = 0
        for (i in 0 until maxTypes) {
            slotUniqueKeys[i] = null
            slotCache[i] = null
            slotPartIndex[i] = 0
        }

        val list = nbt.getTagList("Resources", Constants.NBT.TAG_COMPOUND)
        // temp: build totals by summing stored per-slot counts
        for (i in 0 until list.tagCount()) {
            val keyNbt = list.getCompoundTagAt(i)
            val rawKey = PMItemKeyType.readNBT(keyNbt) as? PMItemKeyImpl ?: continue
            if (rawKey.count <= 0L) continue

            val slotIndex = if (keyNbt.hasKey("Slot", Constants.NBT.TAG_INT)) keyNbt.getInteger("Slot") else -1
            if (slotIndex !in 0 until maxTypes) continue
            if (slotUniqueKeys[slotIndex] != null) continue

            // Clamp per-slot count to slotCap to maintain invariant.
            val clamped = rawKey.count.coerceAtMost(slotCap)
            val uniqueKey = rawKey.uniqueKey

            val realSlot = allocateFreeSlot()
            // allocateFreeSlot gives the lowest free; but we want the exact slotIndex from NBT.
            // Rollback and force-claim slotIndex.
            if (realSlot != slotIndex) {
                // Undo claim of realSlot
                freeSlots.set(realSlot)
                usedSlotCount--
                // Force-claim slotIndex
                if (!freeSlots.get(slotIndex)) {
                    // already claimed; skip
                    continue
                }
                freeSlots.clear(slotIndex)
                usedSlotCount++
            }

            slotUniqueKeys[slotIndex] = uniqueKey
            slotCache[slotIndex] = PMItemKeyImpl(uniqueKey, clamped)
            // We'll normalize partIndex/order later.

            totals[uniqueKey] = (totals[uniqueKey] ?: 0L) + clamped
            keySlots.getOrPut(uniqueKey) { mutableListOf() }.add(slotIndex)
        }

        // Normalize slot order and counts according to totals/cap.
        for ((uniqueKey, total) in totals) {
            val slots = keySlots[uniqueKey] ?: continue
            // keep deterministic order
            slots.sort()
            // shrink/grow to the required slot count, trying to keep existing indices.
            val needed = requiredSlotsForTotal(total)
            // If we already have more than needed, free extras from the end (largest indices).
            while (slots.size > needed) {
                val slot = slots.removeAt(slots.size - 1)
                freeSlot(slot)
            }
            // If we need more, allocate additional free slots.
            while (slots.size < needed) {
                val slot = allocateFreeSlot()
                if (slot < 0) break
                slotUniqueKeys[slot] = uniqueKey
                slotCache[slot] = PMItemKeyImpl(uniqueKey, 0L)
                slots.add(slot)
                markSlotDirty(slot)
            }
            slots.sort()
            for (part in 0 until slots.size) {
                val slot = slots[part]
                slotPartIndex[slot] = part
                val key = slotCache[slot] ?: PMItemKeyImpl(uniqueKey, 0L).also { slotCache[slot] = it }
                val newCount = amountForPart(total, part)
                key.count = newCount
                markSlotDirty(slot)
            }
        }

        pendingChanges = true
    }

    /**
     * Minimal int list to avoid boxing.
     */
    private class IntArrayList {
        private var a: IntArray = IntArray(16)
        private var size: Int = 0

        fun add(v: Int) {
            if (size >= a.size) a = a.copyOf(a.size * 2)
            a[size++] = v
        }

        fun clear() {
            size = 0
        }

        fun toIntArray(): IntArray {
            return a.copyOf(size)
        }
    }
}
