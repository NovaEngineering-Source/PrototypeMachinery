package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.impl.storage.FluidResourceStorage
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandler

/**
 * # FluidHatchBlockEntity - Fluid Hatch Block Entity
 * # FluidHatchBlockEntity - 流体仓方块实体
 *
 * Block entity that provides fluid storage functionality.
 *
 * 提供流体存储功能的方块实体。
 *
 * @param config The configuration for this hatch
 */
public class FluidHatchBlockEntity(
    public var config: FluidHatchConfig
) : BlockEntity(), IGuiHolder<PosGuiData> {

    // Primary constructor for NBT deserialization
    public constructor() : this(FluidHatchConfig.createDefault(1))

    /**
     * The fluid storage.
     * 流体存储。
     */
    public var storage: FluidResourceStorage = createStorage(config)

    private fun createStorage(config: FluidHatchConfig): FluidResourceStorage {
        return FluidResourceStorage(
            maxTypes = config.tankCount,
            maxCountPerType = config.tankCapacity
        )
    }

    /**
     * Applies a new config to this hatch at runtime.
     * Rebuilds storage with new limits and migrates existing contents.
     */
    public fun applyConfig(newConfig: FluidHatchConfig) {
        if (this.config == newConfig) return

        val oldStorage = storage
        val newStorage = createStorage(newConfig)

        for (key in oldStorage.getAllResources()) {
            val amount = oldStorage.getAmount(key)
            if (amount > 0L) {
                newStorage.insert(key, amount, false)
            }
        }

        this.config = newConfig
        this.storage = newStorage

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
    private val fluidHandler: FluidHatchFluidHandler by lazy { FluidHatchFluidHandler(this) }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        compound.setTag("Storage", storage.writeNBT(NBTTagCompound()))
        compound.setBoolean("AutoInput", autoInput)
        compound.setBoolean("AutoOutput", autoOutput)
        compound.setInteger("Tier", config.tier.tier)
        compound.setString("HatchType", config.hatchType.name)
        compound.setInteger("TankCount", config.tankCount)
        compound.setLong("TankCapacity", config.tankCapacity)
        return compound
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        autoInput = compound.getBoolean("AutoInput")
        autoOutput = compound.getBoolean("AutoOutput")

        val tierLevel = if (compound.hasKey("Tier")) compound.getInteger("Tier") else config.tier.tier
        val hatchType = runCatching {
            if (compound.hasKey("HatchType")) HatchType.valueOf(compound.getString("HatchType")) else config.hatchType
        }.getOrElse { config.hatchType }

        config = HatchConfigRegistry.getFluidHatchConfig(tierLevel, hatchType)
        storage = createStorage(config)

        if (compound.hasKey("Storage")) {
            storage.readNBT(compound.getCompoundTag("Storage"))
        }
    }

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true
        }
        return super.hasCapability(capability, facing)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return fluidHandler as T
        }
        return super.getCapability(capability, facing)
    }

    /**
     * Gets the fluid handler for external use.
     * 获取用于外部使用的流体处理器。
     */
    public fun getFluidHandler(): IFluidHandler = fluidHandler

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        return FluidHatchGUI.buildPanel(this, data, syncManager)
    }

}
