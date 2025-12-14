package github.kasuminova.prototypemachinery.common.block.hatch.energy

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.impl.storage.EnergyStorageImpl
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.energy.CapabilityEnergy
import net.minecraftforge.energy.IEnergyStorage

/**
 * # EnergyHatchBlockEntity - Energy Hatch Block Entity
 * # EnergyHatchBlockEntity - 能量仓方块实体
 *
 * Block entity that provides energy storage functionality.
 *
 * 提供能量存储功能的方块实体。
 *
 * @param config The configuration for this hatch
 */
public class EnergyHatchBlockEntity(
    public var config: EnergyHatchConfig
) : BlockEntity(), IGuiHolder<PosGuiData> {

    // Primary constructor for NBT deserialization
    public constructor() : this(EnergyHatchConfig.createDefault(1))

    /**
     * The energy storage.
     * 能量存储。
     */
    public val storage: EnergyStorageImpl = EnergyStorageImpl(
        capacity = config.capacity,
        maxReceive = if (config.hatchType != HatchType.OUTPUT) config.maxTransfer else 0L,
        maxExtract = if (config.hatchType != HatchType.INPUT) config.maxTransfer else 0L
    )

    /**
     * Max input rate (FE/t). 0 means "use default" (i.e. config.maxTransfer).
     * 最大输入速率（FE/t）。0 表示使用默认值（即 config.maxTransfer）。
     */
    private var maxInputRateInternal: Long = 0L

    public var maxInputRate: Long
        get() = maxInputRateInternal
        set(value) {
            maxInputRateInternal = value.coerceAtLeast(0L)
            updateTransferLimits()
            markDirty()
            notifyClientUpdate()
        }

    /**
     * Max output rate (FE/t). 0 means "use default" (i.e. config.maxTransfer).
     * 最大输出速率（FE/t）。0 表示使用默认值（即 config.maxTransfer）。
     */
    private var maxOutputRateInternal: Long = 0L

    public var maxOutputRate: Long
        get() = maxOutputRateInternal
        set(value) {
            maxOutputRateInternal = value.coerceAtLeast(0L)
            updateTransferLimits()
            markDirty()
            notifyClientUpdate()
        }

    private fun effectiveRate(userRate: Long): Long {
        // 0 => default (config.maxTransfer), otherwise clamp to config.maxTransfer.
        val base = config.maxTransfer.coerceAtLeast(0L)
        return if (userRate <= 0L) base else minOf(userRate, base)
    }

    private fun updateTransferLimits() {
        storage.maxReceive = if (config.hatchType != HatchType.OUTPUT) effectiveRate(maxInputRateInternal) else 0L
        storage.maxExtract = if (config.hatchType != HatchType.INPUT) effectiveRate(maxOutputRateInternal) else 0L
    }

    /**
     * Applies a new config to this hatch at runtime.
     * Updates storage parameters in-place.
     */
    public fun applyConfig(newConfig: EnergyHatchConfig) {
        if (this.config == newConfig) return

        this.config = newConfig
        storage.capacity = newConfig.capacity
        updateTransferLimits()

        // Clamp stored energy to new capacity.
        storage.setEnergy(storage.energy)

        markDirty()
        notifyClientUpdate()
    }

    private fun notifyClientUpdate() {
        val w: World = world ?: return
        if (w.isRemote) return
        val state = w.getBlockState(pos)
        w.notifyBlockUpdate(pos, state, state, 3)
    }

    /**
     * Auto-input enabled flag.
     * 自动输入启用标志。
     */
    public var autoInput: Boolean = false

    /**
     * Auto-output enabled flag.
     * 自动输出启用标志。
     */
    public var autoOutput: Boolean = false

    // Capability wrapper
    private val energyHandler: EnergyHatchEnergyStorage by lazy { EnergyHatchEnergyStorage(this) }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        compound.setTag("Storage", storage.writeNBT(NBTTagCompound()))
        compound.setBoolean("AutoInput", autoInput)
        compound.setBoolean("AutoOutput", autoOutput)
        compound.setLong("MaxInRate", maxInputRateInternal)
        compound.setLong("MaxOutRate", maxOutputRateInternal)
        compound.setInteger("Tier", config.tier.tier)
        compound.setString("HatchType", config.hatchType.name)
        compound.setLong("Capacity", config.capacity)
        compound.setLong("MaxTransfer", config.maxTransfer)
        return compound
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        autoInput = compound.getBoolean("AutoInput")
        autoOutput = compound.getBoolean("AutoOutput")
        // Don't trigger notifyClientUpdate() during load; assign backing fields directly.
        maxInputRateInternal = if (compound.hasKey("MaxInRate")) compound.getLong("MaxInRate") else 0L
        maxOutputRateInternal = if (compound.hasKey("MaxOutRate")) compound.getLong("MaxOutRate") else 0L

        val tierLevel = if (compound.hasKey("Tier")) compound.getInteger("Tier") else config.tier.tier
        val hatchType = runCatching {
            if (compound.hasKey("HatchType")) HatchType.valueOf(compound.getString("HatchType")) else config.hatchType
        }.getOrElse { config.hatchType }

        config = HatchConfigRegistry.getEnergyHatchConfig(tierLevel, hatchType)

        val energyFromNbt = if (compound.hasKey("Storage")) {
            compound.getCompoundTag("Storage").getLong("Energy")
        } else 0L

        // Apply config limits (authoritative) and then restore energy amount.
        storage.capacity = config.capacity
        updateTransferLimits()
        storage.setEnergy(energyFromNbt)
    }

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
        if (capability == CapabilityEnergy.ENERGY) {
            return true
        }
        return super.hasCapability(capability, facing)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        if (capability == CapabilityEnergy.ENERGY) {
            return energyHandler as T
        }
        return super.getCapability(capability, facing)
    }

    /**
     * Gets the energy storage for external use.
     * 获取用于外部使用的能量存储。
     */
    public fun getEnergyStorage(): IEnergyStorage = energyHandler

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        return EnergyHatchGUI.buildPanel(this, data, syncManager)
    }

}
