package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.container.StructureFluidKeyContainer
import github.kasuminova.prototypemachinery.api.storage.ResourceStorage
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.capability.IFluidHandler

/**
 * Structure-derived fluid container adapters.
 *
 * Internal recipe/scanning should use the key-level API ([StructureFluidKeyContainer]).
 */
public class StructureFluidContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    private val handler: IFluidHandler,
    private val allowed: Set<PortMode>
) : StructureFluidKeyContainer {

    private companion object {
        private const val VANILLA_STACK_CAP: Int = Int.MAX_VALUE / 2
    }

    override fun isAllowedPortMode(mode: PortMode): Boolean = allowed.contains(mode)

    override fun insert(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.INPUT)) return 0L
        return insertUnchecked(key, amount, mode)
    }

    override fun insertUnchecked(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L

        val doFill = mode == TransactionMode.EXECUTE
        val proto = key.get()

        var remaining = amount
        var insertedTotal = 0L

        while (remaining > 0L) {
            val toFill = minOf(remaining, VANILLA_STACK_CAP.toLong()).toInt()
            val filled = handler.fill(FluidStack(proto.fluid, toFill, proto.tag), doFill)
            if (filled <= 0) break
            insertedTotal += filled.toLong()
            remaining -= filled.toLong()
        }

        return insertedTotal
    }

    override fun extract(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L
        return extractUnchecked(key, amount, mode)
    }

    override fun extractUnchecked(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L

        val doDrain = mode == TransactionMode.EXECUTE
        val proto = key.get()

        var remaining = amount
        var drainedTotal = 0L

        while (remaining > 0L) {
            val toDrain = minOf(remaining, VANILLA_STACK_CAP.toLong()).toInt()
            val drained = handler.drain(FluidStack(proto.fluid, toDrain, proto.tag), doDrain) ?: break
            if (drained.amount <= 0) break
            drainedTotal += drained.amount.toLong()
            remaining -= drained.amount.toLong()
        }

        return drainedTotal
    }
}

/**
 * Storage-backed fluid container component.
 *
 * Uses PMKey-based storage directly and only materializes FluidStack at the capability boundary.
 */
public class StructureFluidStorageContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    public val storage: ResourceStorage<PMKey<FluidStack>>,
    public val allowed: Set<PortMode>
) : StructureFluidKeyContainer {

    override fun isAllowedPortMode(mode: PortMode): Boolean = allowed.contains(mode)

    override fun insert(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.INPUT)) return 0L
        return insertUnchecked(key, amount, mode)
    }

    override fun insertUnchecked(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        val simulate = mode == TransactionMode.SIMULATE
        return storage.insert(key, amount, simulate)
    }

    override fun extract(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L
        return extractUnchecked(key, amount, mode)
    }

    override fun extractUnchecked(key: PMKey<FluidStack>, amount: Long, mode: TransactionMode): Long {
        if (amount <= 0L) return 0L
        val simulate = mode == TransactionMode.SIMULATE
        return storage.extract(key, amount, simulate)
    }
}
