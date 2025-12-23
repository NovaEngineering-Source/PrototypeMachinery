package github.kasuminova.prototypemachinery.impl.machine.component.container

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponent
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.api.util.TransactionMode
import net.minecraftforge.energy.IEnergyStorage

public interface StructureEnergyContainer : StructureComponent {

    public val capacity: Long

    public val stored: Long

    public fun isAllowedPortMode(ioType: PortMode): Boolean

    public fun insertEnergy(amount: Long, action: TransactionMode): Long

    public fun extractEnergy(amount: Long, action: TransactionMode): Long

    /**
     * Unchecked variant that ignores PortMode restrictions.
     *
     * 用于事务 rollback 的“内部操作”接口：忽略 PortMode 限制以恢复容器原始状态。
     */
    public fun insertEnergyUnchecked(amount: Long, action: TransactionMode): Long

    /** Unchecked variant that ignores PortMode restrictions. / rollback 用：忽略 PortMode 限制 */
    public fun extractEnergyUnchecked(amount: Long, action: TransactionMode): Long
}

public class StructureEnergyContainerComponent(
    override val owner: MachineInstance,
    override val provider: Any? = null,
    private val storage: IEnergyStorage,
    private val allowed: Set<PortMode>
) : StructureEnergyContainer {

    override val capacity: Long
        get() = storage.maxEnergyStored.toLong()

    override val stored: Long
        get() = storage.energyStored.toLong()

    override fun isAllowedPortMode(ioType: PortMode): Boolean = allowed.contains(ioType)

    override fun insertEnergy(amount: Long, action: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.INPUT)) return 0L

        return insertEnergyUnchecked(amount, action)
    }

    override fun insertEnergyUnchecked(amount: Long, action: TransactionMode): Long {
        if (amount <= 0L) return 0L

        val simulate = action == TransactionMode.SIMULATE
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

    override fun extractEnergy(amount: Long, action: TransactionMode): Long {
        if (amount <= 0L) return 0L
        if (!isAllowedPortMode(PortMode.OUTPUT)) return 0L

        return extractEnergyUnchecked(amount, action)
    }

    override fun extractEnergyUnchecked(amount: Long, action: TransactionMode): Long {
        if (amount <= 0L) return 0L

        val simulate = action == TransactionMode.SIMULATE
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
