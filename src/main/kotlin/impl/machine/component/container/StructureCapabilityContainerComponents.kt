package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponent
import github.kasuminova.prototypemachinery.api.storage.ResourceStorage
import github.kasuminova.prototypemachinery.api.storage.SlottedResourceStorage
import github.kasuminova.prototypemachinery.common.util.Action
import github.kasuminova.prototypemachinery.common.util.IOType
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import github.kasuminova.prototypemachinery.impl.storage.ItemResourceStorage
import net.minecraft.item.ItemStack
import net.minecraftforge.energy.IEnergyStorage
import net.minecraftforge.fluids.Fluid
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.items.IItemHandler
import net.minecraftforge.items.IItemHandlerModifiable
import net.minecraftforge.items.ItemHandlerHelper

/**
 * Structure-derived container component adapters.
 *
 * These are created during structure forming/refresh and stored in [github.kasuminova.prototypemachinery.api.machine.component.StructureComponentMap].
 * They do NOT have machine systems.
 */

// ---------------------------------------------------------------------------
// Item
// ---------------------------------------------------------------------------

public interface StructureItemContainer : StructureComponent {

    public val slots: Int

    public val maxStackSize: Long

    public fun isAllowedIOType(ioType: IOType): Boolean

    public fun getItem(slot: Int): ItemStack

    public fun setItem(slot: Int, item: ItemStack)

    public fun insertItem(stack: ItemStack, action: Action): InsertResult

    public fun extractItem(amount: Long, action: Action, predicate: (ItemStack) -> Boolean): ExtractResult

    public sealed interface InsertResult {
        public data class Success(val remaining: ItemStack) : InsertResult
        public object Full : InsertResult
    }

    public sealed interface ExtractResult {
        public data class Success(val extracted: ItemStack) : ExtractResult
        public object Empty : ExtractResult
    }
}

public class StructureItemContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    private val handler: IItemHandler,
    private val allowed: Set<IOType>
) : StructureItemContainer {

    override val slots: Int
        get() = handler.slots

    override val maxStackSize: Long
        get() {
            var max = 0
            for (i in 0 until handler.slots) {
                max = maxOf(max, handler.getSlotLimit(i))
            }
            return max.toLong()
        }

    override fun isAllowedIOType(ioType: IOType): Boolean = allowed.contains(ioType)

    override fun getItem(slot: Int): ItemStack = handler.getStackInSlot(slot)

    override fun setItem(slot: Int, item: ItemStack) {
        val mod = handler as? IItemHandlerModifiable ?: return
        mod.setStackInSlot(slot, item)
    }

    override fun insertItem(stack: ItemStack, action: Action): StructureItemContainer.InsertResult {
        if (stack.isEmpty) return StructureItemContainer.InsertResult.Success(ItemStack.EMPTY)
        if (!isAllowedIOType(IOType.INPUT)) return StructureItemContainer.InsertResult.Full

        val simulate = action == Action.SIMULATE
        var remaining = stack
        for (i in 0 until handler.slots) {
            if (remaining.isEmpty) break
            remaining = handler.insertItem(i, remaining, simulate)
        }

        return if (remaining === stack || remaining.count == stack.count) {
            StructureItemContainer.InsertResult.Full
        } else {
            StructureItemContainer.InsertResult.Success(remaining)
        }
    }

    override fun extractItem(
        amount: Long,
        action: Action,
        predicate: (ItemStack) -> Boolean
    ): StructureItemContainer.ExtractResult {
        if (amount <= 0L) return StructureItemContainer.ExtractResult.Empty
        if (!isAllowedIOType(IOType.OUTPUT)) return StructureItemContainer.ExtractResult.Empty

        val simulate = action == Action.SIMULATE
        var remaining = amount
        var extracted = ItemStack.EMPTY

        for (i in 0 until handler.slots) {
            if (remaining <= 0L) break

            val inSlot = handler.getStackInSlot(i)
            if (inSlot.isEmpty) continue
            if (!predicate(inSlot)) continue

            // If we already picked a type, only extract stackable items.
            if (!extracted.isEmpty && !ItemHandlerHelper.canItemStacksStack(extracted, inSlot)) continue

            val toExtract = minOf(remaining, inSlot.count.toLong()).toInt()
            val got = handler.extractItem(i, toExtract, simulate)
            if (got.isEmpty) continue

            if (extracted.isEmpty) {
                extracted = got.copy()
            } else {
                extracted.grow(got.count)
            }

            remaining -= got.count.toLong()
        }

        return if (extracted.isEmpty) {
            StructureItemContainer.ExtractResult.Empty
        } else {
            StructureItemContainer.ExtractResult.Success(extracted)
        }
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
    private val storage: SlottedResourceStorage<PMKey<ItemStack>>,
    private val allowed: Set<IOType>
) : StructureItemContainer {

    override val slots: Int
        get() = storage.slotCount

    override val maxStackSize: Long
        get() = storage.maxCountPerType

    override fun isAllowedIOType(ioType: IOType): Boolean = allowed.contains(ioType)

    override fun getItem(slot: Int): ItemStack {
        return storage.getSlot(slot)?.get() ?: ItemStack.EMPTY
    }

    override fun setItem(slot: Int, item: ItemStack) {
        if (slot !in 0 until storage.slotCount) return

        // Best-effort implementation:
        // - Clear the slot's current part
        // - Insert the new stack amount (may re-layout virtual slots)
        storage.extractFromSlot(slot, Long.MAX_VALUE, false)

        if (item.isEmpty) return
        val key = PMItemKeyType.create(item)
        storage.insert(key, item.count.toLong(), false)
    }

    override fun insertItem(stack: ItemStack, action: Action): StructureItemContainer.InsertResult {
        if (stack.isEmpty) return StructureItemContainer.InsertResult.Success(ItemStack.EMPTY)
        if (!isAllowedIOType(IOType.INPUT)) return StructureItemContainer.InsertResult.Full

        val simulate = action == Action.SIMULATE
        val key = PMItemKeyType.create(stack)
        val inserted = storage.insert(key, stack.count.toLong(), simulate)
        if (inserted <= 0L) return StructureItemContainer.InsertResult.Full

        val remainingCount = (stack.count.toLong() - inserted).coerceAtLeast(0L)
        if (remainingCount <= 0L) return StructureItemContainer.InsertResult.Success(ItemStack.EMPTY)

        val remaining = stack.copy()
        remaining.count = remainingCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return StructureItemContainer.InsertResult.Success(remaining)
    }

    override fun extractItem(
        amount: Long,
        action: Action,
        predicate: (ItemStack) -> Boolean
    ): StructureItemContainer.ExtractResult {
        if (amount <= 0L) return StructureItemContainer.ExtractResult.Empty
        if (!isAllowedIOType(IOType.OUTPUT)) return StructureItemContainer.ExtractResult.Empty

        val simulate = action == Action.SIMULATE

        val keys: Collection<PMKey<ItemStack>> = when (storage) {
            is ItemResourceStorage -> storage.getResourceTypes()
            else -> storage.getAllResources().distinct()
        }

        // Find one matching type and extract from that type.
        for (key in keys) {
            val probe = key.get().copy().apply { count = 1 }
            if (!predicate(probe)) continue

            val extracted = storage.extract(key, amount, simulate)
            if (extracted <= 0L) continue

            val out = key.get()
            out.count = extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            return StructureItemContainer.ExtractResult.Success(out)
        }

        return StructureItemContainer.ExtractResult.Empty
    }
}

// ---------------------------------------------------------------------------
// Fluid
// ---------------------------------------------------------------------------

public interface StructureFluidContainer : StructureComponent {

    public val tanks: Int

    public val maxFluidAmount: Long

    public fun isAllowedIOType(ioType: IOType): Boolean

    public fun getFluidAmount(tank: Int): Long

    public fun setFluidAmount(tank: Int, amount: Long)

    public fun insertFluid(fluid: FluidStack, amount: Long, action: Action): InsertResult

    public fun extractFluid(fluid: Fluid, amount: Long, action: Action): ExtractResult

    /**
     * Unchecked variant that ignores IOType restrictions.
     *
     * 用于事务 rollback 的“内部操作”接口：忽略 IOType 限制以恢复容器原始状态。
     */
    public fun insertFluidUnchecked(fluid: FluidStack, amount: Long, action: Action): InsertResult

    /** Unchecked variant that ignores IOType restrictions. / rollback 用：忽略 IOType 限制 */
    public fun extractFluidUnchecked(fluid: Fluid, amount: Long, action: Action): ExtractResult

    public sealed interface InsertResult {
        public data class Success(val remaining: Long) : InsertResult
        public object Full : InsertResult
    }

    public sealed interface ExtractResult {
        public data class Success(val extracted: FluidStack, val amount: Long) : ExtractResult
        public object Empty : ExtractResult
    }
}

public class StructureFluidContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    private val handler: IFluidHandler,
    private val allowed: Set<IOType>
) : StructureFluidContainer {

    private val props: Array<out net.minecraftforge.fluids.capability.IFluidTankProperties>
        get() = handler.tankProperties

    override val tanks: Int
        get() = props.size

    override val maxFluidAmount: Long
        get() {
            var sum = 0L
            for (p in props) {
                sum += p.capacity.toLong()
            }
            return sum
        }

    override fun isAllowedIOType(ioType: IOType): Boolean = allowed.contains(ioType)

    override fun getFluidAmount(tank: Int): Long {
        val p = props.getOrNull(tank) ?: return 0L
        return p.contents?.amount?.toLong() ?: 0L
    }

    override fun setFluidAmount(tank: Int, amount: Long) {
        // Not supported via IFluidHandler.
    }

    override fun insertFluid(fluid: FluidStack, amount: Long, action: Action): StructureFluidContainer.InsertResult {
        if (amount <= 0L) return StructureFluidContainer.InsertResult.Success(0L)
        if (!isAllowedIOType(IOType.INPUT)) return StructureFluidContainer.InsertResult.Full

        return insertFluidUnchecked(fluid, amount, action)
    }

    override fun insertFluidUnchecked(fluid: FluidStack, amount: Long, action: Action): StructureFluidContainer.InsertResult {
        if (amount <= 0L) return StructureFluidContainer.InsertResult.Success(0L)

        val doFill = action == Action.EXECUTE
        var remaining = amount

        while (remaining > 0L) {
            val toFill = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            val filled = handler.fill(FluidStack(fluid.fluid, toFill, fluid.tag), doFill)
            if (filled <= 0) break
            remaining -= filled.toLong()
        }

        return if (remaining == amount) {
            StructureFluidContainer.InsertResult.Full
        } else {
            StructureFluidContainer.InsertResult.Success(remaining)
        }
    }

    override fun extractFluid(fluid: Fluid, amount: Long, action: Action): StructureFluidContainer.ExtractResult {
        if (amount <= 0L) return StructureFluidContainer.ExtractResult.Empty
        if (!isAllowedIOType(IOType.OUTPUT)) return StructureFluidContainer.ExtractResult.Empty

        return extractFluidUnchecked(fluid, amount, action)
    }

    override fun extractFluidUnchecked(fluid: Fluid, amount: Long, action: Action): StructureFluidContainer.ExtractResult {
        if (amount <= 0L) return StructureFluidContainer.ExtractResult.Empty

        val doDrain = action == Action.EXECUTE
        var remaining = amount
        var drainedTotal = 0L

        while (remaining > 0L) {
            val toDrain = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            val drained = handler.drain(FluidStack(fluid, toDrain), doDrain) ?: break
            if (drained.amount <= 0) break
            drainedTotal += drained.amount.toLong()
            remaining -= drained.amount.toLong()
        }

        return if (drainedTotal <= 0L) {
            StructureFluidContainer.ExtractResult.Empty
        } else {
            val stack = FluidStack(fluid, drainedTotal.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            StructureFluidContainer.ExtractResult.Success(stack, drainedTotal)
        }
    }
}

/**
 * Storage-backed fluid container component.
 *
 * Uses PMKey-based storage directly and only materializes FluidStack at the API boundary.
 */
public class StructureFluidStorageContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    private val storage: ResourceStorage<PMKey<FluidStack>>,
    private val allowed: Set<IOType>
) : StructureFluidContainer {

    override val tanks: Int
        get() = storage.maxTypes

    override val maxFluidAmount: Long
        get() {
            val perType = storage.maxCountPerType
            val types = storage.maxTypes.toLong()
            if (perType == Long.MAX_VALUE) return Long.MAX_VALUE
            return runCatching { Math.multiplyExact(perType, types) }.getOrElse { Long.MAX_VALUE }
        }

    override fun isAllowedIOType(ioType: IOType): Boolean = allowed.contains(ioType)

    override fun getFluidAmount(tank: Int): Long {
        if (tank !in 0 until tanks) return 0L
        val snapshot = storage.getAllResources().toList()
        return snapshot.getOrNull(tank)?.count ?: 0L
    }

    override fun setFluidAmount(tank: Int, amount: Long) {
        // Not supported by generic storage API.
    }

    override fun insertFluid(fluid: FluidStack, amount: Long, action: Action): StructureFluidContainer.InsertResult {
        if (amount <= 0L) return StructureFluidContainer.InsertResult.Success(0L)
        if (!isAllowedIOType(IOType.INPUT)) return StructureFluidContainer.InsertResult.Full

        return insertFluidUnchecked(fluid, amount, action)
    }

    override fun insertFluidUnchecked(fluid: FluidStack, amount: Long, action: Action): StructureFluidContainer.InsertResult {
        if (amount <= 0L) return StructureFluidContainer.InsertResult.Success(0L)

        val simulate = action == Action.SIMULATE
        val key = PMFluidKeyType.create(FluidStack(fluid.fluid, 1, fluid.tag))
        val inserted = storage.insert(key, amount, simulate)
        if (inserted <= 0L) return StructureFluidContainer.InsertResult.Full

        val remaining = (amount - inserted).coerceAtLeast(0L)
        return StructureFluidContainer.InsertResult.Success(remaining)
    }

    override fun extractFluid(fluid: Fluid, amount: Long, action: Action): StructureFluidContainer.ExtractResult {
        if (amount <= 0L) return StructureFluidContainer.ExtractResult.Empty
        if (!isAllowedIOType(IOType.OUTPUT)) return StructureFluidContainer.ExtractResult.Empty

        return extractFluidUnchecked(fluid, amount, action)
    }

    override fun extractFluidUnchecked(fluid: Fluid, amount: Long, action: Action): StructureFluidContainer.ExtractResult {
        if (amount <= 0L) return StructureFluidContainer.ExtractResult.Empty

        val simulate = action == Action.SIMULATE
        val key = PMFluidKeyType.create(FluidStack(fluid, 1))
        val extracted = storage.extract(key, amount, simulate)
        if (extracted <= 0L) return StructureFluidContainer.ExtractResult.Empty

        val out = FluidStack(fluid, extracted.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        return StructureFluidContainer.ExtractResult.Success(out, extracted)
    }
}

// ---------------------------------------------------------------------------
// Energy
// ---------------------------------------------------------------------------

public interface StructureEnergyContainer : StructureComponent {

    public val capacity: Long

    public val stored: Long

    public fun isAllowedIOType(ioType: IOType): Boolean

    public fun insertEnergy(amount: Long, action: Action): Long

    public fun extractEnergy(amount: Long, action: Action): Long

    /**
     * Unchecked variant that ignores IOType restrictions.
     *
     * 用于事务 rollback 的“内部操作”接口：忽略 IOType 限制以恢复容器原始状态。
     */
    public fun insertEnergyUnchecked(amount: Long, action: Action): Long

    /** Unchecked variant that ignores IOType restrictions. / rollback 用：忽略 IOType 限制 */
    public fun extractEnergyUnchecked(amount: Long, action: Action): Long
}

public class StructureEnergyContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    private val storage: IEnergyStorage,
    private val allowed: Set<IOType>
) : StructureEnergyContainer {

    override val capacity: Long
        get() = storage.maxEnergyStored.toLong()

    override val stored: Long
        get() = storage.energyStored.toLong()

    override fun isAllowedIOType(ioType: IOType): Boolean = allowed.contains(ioType)

    override fun insertEnergy(amount: Long, action: Action): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedIOType(IOType.INPUT)) return 0L

        return insertEnergyUnchecked(amount, action)
    }

    override fun insertEnergyUnchecked(amount: Long, action: Action): Long {
        if (amount <= 0L) return 0L

        val simulate = action == Action.SIMULATE
        var remaining = amount
        var receivedTotal = 0L

        while (remaining > 0L) {
            val toReceive = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            val received = storage.receiveEnergy(toReceive, simulate)
            if (received <= 0) break
            receivedTotal += received.toLong()
            remaining -= received.toLong()
        }

        return receivedTotal
    }

    override fun extractEnergy(amount: Long, action: Action): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedIOType(IOType.OUTPUT)) return 0L

        return extractEnergyUnchecked(amount, action)
    }

    override fun extractEnergyUnchecked(amount: Long, action: Action): Long {
        if (amount <= 0L) return 0L

        val simulate = action == Action.SIMULATE
        var remaining = amount
        var extractedTotal = 0L

        while (remaining > 0L) {
            val toExtract = minOf(remaining, Int.MAX_VALUE.toLong()).toInt()
            val extracted = storage.extractEnergy(toExtract, simulate)
            if (extracted <= 0) break
            extractedTotal += extracted.toLong()
            remaining -= extracted.toLong()
        }

        return extractedTotal
    }
}
