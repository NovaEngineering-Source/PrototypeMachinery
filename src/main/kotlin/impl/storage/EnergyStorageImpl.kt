package github.kasuminova.prototypemachinery.impl.storage

import net.minecraft.nbt.NBTTagCompound

/**
 * # EnergyStorage - Simple Energy Storage
 * # EnergyStorage - 简单能量存储
 *
 * A simple energy storage implementation that supports Long values.
 * Compatible with Forge Energy (FE/RF) but internally uses Long for large values.
 *
 * 支持 Long 值的简单能量存储实现。
 * 与 Forge Energy (FE/RF) 兼容，但内部使用 Long 以支持大数值。
 *
 * @param capacity Maximum energy capacity
 * @param maxReceive Maximum energy that can be received per operation
 * @param maxExtract Maximum energy that can be extracted per operation
 */
public class EnergyStorageImpl(
    public var capacity: Long,
    public var maxReceive: Long = capacity,
    public var maxExtract: Long = capacity
) {

    /**
     * Current stored energy.
     * 当前存储的能量。
     */
    @Volatile
    public var energy: Long = 0L
        private set

    /**
     * Pending changes flag for sync optimization.
     * 待处理变更标志，用于同步优化。
     */
    @Volatile
    private var pendingChanges: Boolean = false

    /**
     * Change listener for energy changes.
     * 能量变更的监听器。
     */
    public var changeListener: ((previousEnergy: Long, newEnergy: Long) -> Unit)? = null

    /**
     * Whether this storage is empty.
     * 此存储是否为空。
     */
    public val isEmpty: Boolean
        get() = energy <= 0

    /**
     * Whether this storage is full.
     * 此存储是否已满。
     */
    public val isFull: Boolean
        get() = energy >= capacity

    /**
     * Receives energy into the storage.
     *
     * 向存储接收能量。
     *
     * @param amount The amount of energy to receive
     * @param simulate If true, only simulates the operation
     * @return The amount of energy that was actually received
     */
    public fun receiveEnergy(amount: Long, simulate: Boolean): Long {
        if (amount <= 0 || maxReceive <= 0) return 0L

        val received = minOf(amount, maxReceive, capacity - energy)
        if (received <= 0) return 0L

        if (!simulate) {
            val previousEnergy = energy
            energy += received
            pendingChanges = true
            changeListener?.invoke(previousEnergy, energy)
        }

        return received
    }

    /**
     * Receives energy with Long precision (alias).
     * 以长整型精度接收能量（别名）。
     */
    public fun receiveEnergyLong(maxReceive: Long, simulate: Boolean): Long {
        return receiveEnergy(maxReceive, simulate)
    }

    /**
     * Extracts energy from the storage.
     *
     * 从存储中提取能量。
     *
     * @param amount The maximum amount of energy to extract
     * @param simulate If true, only simulates the operation
     * @return The amount of energy that was actually extracted
     */
    public fun extractEnergy(amount: Long, simulate: Boolean): Long {
        if (amount <= 0 || maxExtract <= 0) return 0L

        val extracted = minOf(amount, maxExtract, energy)
        if (extracted <= 0) return 0L

        if (!simulate) {
            val previousEnergy = energy
            energy -= extracted
            pendingChanges = true
            changeListener?.invoke(previousEnergy, energy)
        }

        return extracted
    }

    /**
     * Extracts energy with Long precision (alias).
     * 以长整型精度提取能量（别名）。
     */
    public fun extractEnergyLong(maxExtract: Long, simulate: Boolean): Long {
        return extractEnergy(maxExtract, simulate)
    }

    /**
     * Sets the energy directly (for sync purposes).
     *
     * 直接设置能量（用于同步目的）。
     */
    public fun setEnergy(amount: Long) {
        val previousEnergy = energy
        energy = amount.coerceIn(0, capacity)
        if (previousEnergy != energy) {
            pendingChanges = true
            changeListener?.invoke(previousEnergy, energy)
        }
    }

    /**
     * Checks if there are pending changes.
     * 检查是否有待处理的变更。
     */
    public fun hasPendingChanges(): Boolean = pendingChanges

    /**
     * Clears the pending changes flag.
     * 清除待处理变更标志。
     */
    public fun clearPendingChanges() {
        pendingChanges = false
    }

    /**
     * Writes this storage to NBT.
     * 将此存储写入 NBT。
     */
    public fun writeNBT(nbt: NBTTagCompound): NBTTagCompound {
        nbt.setLong("Energy", energy)
        nbt.setLong("Capacity", capacity)
        nbt.setLong("MaxReceive", maxReceive)
        nbt.setLong("MaxExtract", maxExtract)
        return nbt
    }

    /**
     * Reads this storage from NBT.
     * 从 NBT 读取此存储。
     */
    public fun readNBT(nbt: NBTTagCompound) {
        energy = nbt.getLong("Energy")
        if (nbt.hasKey("Capacity")) {
            capacity = nbt.getLong("Capacity")
        }
        if (nbt.hasKey("MaxReceive")) {
            maxReceive = nbt.getLong("MaxReceive")
        }
        if (nbt.hasKey("MaxExtract")) {
            maxExtract = nbt.getLong("MaxExtract")
        }
        energy = energy.coerceIn(0, capacity)
    }

    // region Forge Energy Compatibility

    /**
     * Receives energy (int version for Forge compatibility).
     * 接收能量（int 版本，用于 Forge 兼容）。
     */
    public fun receiveEnergyInt(maxReceive: Int, simulate: Boolean): Int {
        return receiveEnergy(maxReceive.toLong(), simulate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Extracts energy (int version for Forge compatibility).
     * 提取能量（int 版本，用于 Forge 兼容）。
     */
    public fun extractEnergyInt(maxExtract: Int, simulate: Boolean): Int {
        return extractEnergy(maxExtract.toLong(), simulate).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Gets energy stored (int version for Forge compatibility).
     * 获取存储的能量（int 版本，用于 Forge 兼容）。
     */
    public fun getEnergyStoredInt(): Int {
        return energy.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    /**
     * Gets max energy stored (int version for Forge compatibility).
     * 获取最大能量存储（int 版本，用于 Forge 兼容）。
     */
    public fun getMaxEnergyStoredInt(): Int {
        return capacity.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    // endregion

}
