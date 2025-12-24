package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.container.EnumerableItemKeyContainer
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureItemKeyContainer
import github.kasuminova.prototypemachinery.api.storage.SlottedResourceStorage
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import net.minecraft.item.ItemStack
import net.minecraftforge.items.IItemHandler

/**
 * Structure-derived item container adapters.
 *
 * Internal recipe/scanning should use the key-level API ([StructureItemKeyContainer]).
 * This file keeps capability-backed integration while avoiding stack-level container interfaces.
 */
public class StructureItemContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    private val handler: IItemHandler,
    private val allowed: Set<PortMode>
) : StructureItemKeyContainer, EnumerableItemKeyContainer {

    private companion object {
        // Forge IItemHandler is Int-based; keep external interaction bounded.
        private const val VANILLA_STACK_CAP: Int = Int.MAX_VALUE / 2
    }

    public val slots: Int
        get() = handler.slots

    public val maxStackSize: Long
        get() {
            var max = 0
            for (i in 0 until handler.slots) {
                max = maxOf(max, handler.getSlotLimit(i))
            }
            return max.toLong()
        }

    override fun isAllowedPortMode(mode: PortMode): Boolean = allowed.contains(mode)

    override fun insert(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.INPUT)) return 0L
        return insertUnchecked(key, amount, mode)
    }

    override fun insertUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L

        val simulate = mode == TransactionMode.SIMULATE
        val proto = (key as? PMItemKey)?.uniqueKey?.getItemStackUnsafe() ?: key.get()

        var remaining = amount
        var insertedTotal = 0L

        while (remaining > 0L) {
            val chunk = minOf(remaining, VANILLA_STACK_CAP.toLong()).toInt()
            var stack = proto.copy().also { it.count = chunk }

            for (i in 0 until handler.slots) {
                if (stack.isEmpty) break
                stack = handler.insertItem(i, stack, simulate)
            }

            val inserted = (chunk - stack.count).coerceAtLeast(0)
            if (inserted <= 0) break

            insertedTotal += inserted.toLong()
            remaining -= inserted.toLong()
        }

        return insertedTotal
    }

    override fun extract(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L
        return extractUnchecked(key, amount, mode)
    }

    override fun extractUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L

        val simulate = mode == TransactionMode.SIMULATE
        val wanted = (key as? PMItemKey)?.uniqueKey ?: PMItemKeyType.getUniqueKey(key.get())

        var remaining = amount
        var extractedTotal = 0L

        while (remaining > 0L) {
            val chunk = minOf(remaining, VANILLA_STACK_CAP.toLong()).toInt()
            var leftInChunk = chunk

            for (i in 0 until handler.slots) {
                if (leftInChunk <= 0) break

                val inSlot = handler.getStackInSlot(i)
                if (inSlot.isEmpty) continue
                if (PMItemKeyType.getUniqueKey(inSlot) != wanted) continue

                val toExtract = minOf(leftInChunk, inSlot.count)
                val got = handler.extractItem(i, toExtract, simulate)
                if (got.isEmpty) continue

                extractedTotal += got.count.toLong()
                remaining -= got.count.toLong()
                leftInChunk -= got.count

                if (remaining <= 0L) break
            }

            if (leftInChunk == chunk) {
                // No progress in this chunk.
                break
            }
        }

        return extractedTotal
    }

    override fun getAllKeysSnapshot(): Collection<PMKey<ItemStack>> {
        val out = LinkedHashSet<PMKey<ItemStack>>()
        for (i in 0 until handler.slots) {
            val s = handler.getStackInSlot(i)
            if (s.isEmpty) continue

            // Snapshot the concrete variant (including NBT). Count is irrelevant for key equality.
            val copy = s.copy()
            copy.count = 1
            out += PMItemKeyType.create(copy)
        }
        return out
    }
}

/**
 * Storage-backed item container component.
 *
 * Prefer using this for PM hatches since it avoids per-slot ItemStack churn.
 */
public class StructureItemStorageContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    public val storage: SlottedResourceStorage<PMKey<ItemStack>>,
    public val allowed: Set<PortMode>
) : StructureItemKeyContainer, EnumerableItemKeyContainer {

    public val slots: Int
        get() = storage.slotCount

    public val maxStackSize: Long
        get() = storage.maxCountPerType

    override fun isAllowedPortMode(mode: PortMode): Boolean = allowed.contains(mode)

    override fun insert(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.INPUT)) return 0L
        return insertUnchecked(key, amount, mode)
    }

    override fun insertUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        val simulate = mode == TransactionMode.SIMULATE
        return storage.insert(key, amount, simulate)
    }

    override fun extract(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L
        return extractUnchecked(key, amount, mode)
    }

    override fun extractUnchecked(key: PMKey<ItemStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        val simulate = mode == TransactionMode.SIMULATE
        return storage.extract(key, amount, simulate)
    }

    override fun getAllKeysSnapshot(): Collection<PMKey<ItemStack>> {
        return storage.getAllResources()
    }
}
