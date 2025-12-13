package github.kasuminova.prototypemachinery.common.block.hatch.energy

import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import net.minecraftforge.energy.IEnergyStorage

/**
 * # EnergyHatchEnergyStorage - Energy Hatch Energy Storage Handler
 * # EnergyHatchEnergyStorage - 能量仓能量存储处理器
 *
 * IEnergyStorage wrapper for EnergyHatchBlockEntity's storage.
 *
 * EnergyHatchBlockEntity 存储的 IEnergyStorage 包装器。
 */
public class EnergyHatchEnergyStorage(
    private val blockEntity: EnergyHatchBlockEntity
) : IEnergyStorage {

    override fun receiveEnergy(maxReceive: Int, simulate: Boolean): Int {
        if (!canReceive()) return 0
        return blockEntity.storage.receiveEnergyInt(maxReceive, simulate)
    }

    override fun extractEnergy(maxExtract: Int, simulate: Boolean): Int {
        if (!canExtract()) return 0
        return blockEntity.storage.extractEnergyInt(maxExtract, simulate)
    }

    override fun getEnergyStored(): Int {
        return blockEntity.storage.getEnergyStoredInt()
    }

    override fun getMaxEnergyStored(): Int {
        return blockEntity.storage.getMaxEnergyStoredInt()
    }

    override fun canExtract(): Boolean {
        return blockEntity.config.hatchType != HatchType.INPUT
    }

    override fun canReceive(): Boolean {
        return blockEntity.config.hatchType != HatchType.OUTPUT
    }

    /**
     * Gets the energy stored as a Long value.
     * 获取长整型能量存储值。
     */
    public fun getEnergyStoredLong(): Long {
        return blockEntity.storage.energy
    }

    /**
     * Gets the max energy capacity as a Long value.
     * 获取长整型最大能量容量值。
     */
    public fun getMaxEnergyStoredLong(): Long {
        return blockEntity.storage.capacity
    }

    /**
     * Receives energy with Long precision.
     * 以长整型精度接收能量。
     */
    public fun receiveEnergyLong(maxReceive: Long, simulate: Boolean): Long {
        if (!canReceive()) return 0L
        return blockEntity.storage.receiveEnergyLong(maxReceive, simulate)
    }

    /**
     * Extracts energy with Long precision.
     * 以长整型精度提取能量。
     */
    public fun extractEnergyLong(maxExtract: Long, simulate: Boolean): Long {
        if (!canExtract()) return 0L
        return blockEntity.storage.extractEnergyLong(maxExtract, simulate)
    }

}
