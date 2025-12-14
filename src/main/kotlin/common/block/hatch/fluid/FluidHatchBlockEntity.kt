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
import net.minecraft.util.ITickable
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fluids.FluidUtil
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandler
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.ItemStackHandler

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
) : BlockEntity(), ITickable, IGuiHolder<PosGuiData> {

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

    /**
     * Max input rate (mB/t). 0 means unlimited.
     * 最大输入速率（mB/t）。0 表示不限制。
     */
    public var maxInputRate: Long = 0L
        set(value) {
            val v = value.coerceAtLeast(0L)
            if (field == v) return
            field = v
            markDirty()
            notifyClientUpdate()
        }

    /**
     * Max output rate (mB/t). 0 means unlimited.
     * 最大输出速率（mB/t）。0 表示不限制。
     */
    public var maxOutputRate: Long = 0L
        set(value) {
            val v = value.coerceAtLeast(0L)
            if (field == v) return
            field = v
            markDirty()
            notifyClientUpdate()
        }

    /**
     * 2-slot item handler for fluid containers.
     * Slot 0: input container (drain into internal storage)
     * Slot 1: output container (fill from internal storage)
     */
    private val containerSlots: ItemStackHandler = object : ItemStackHandler(2) {
        override fun getSlotLimit(slot: Int): Int = 1
    }

    public fun getContainerSlots(): ItemStackHandler = containerSlots

    public fun clearFluids() {
        storage.clear()
        markDirty()
        notifyClientUpdate()
    }

    // Capability wrapper
    private val fluidHandler: FluidHatchFluidHandler by lazy { FluidHatchFluidHandler(this) }

    override fun update() {
        val w = world ?: return
        if (w.isRemote) return

        // Auto drain/fill containers.
        // Keep it simple: process in-place, 1 stack per slot, once per tick.
        tryDrainContainer()
        tryFillContainer()
    }

    private fun tryDrainContainer() {
        val inStack = containerSlots.getStackInSlot(0)
        if (inStack.isEmpty) return

        val result = FluidUtil.tryEmptyContainer(inStack, getFluidHandler(), Int.MAX_VALUE, null, true)
        if (!result.isSuccess) return

        val remaining = result.result
        if (remaining == inStack) return

        containerSlots.setStackInSlot(0, remaining)
        markDirty()
    }

    private fun tryFillContainer() {
        val outStack = containerSlots.getStackInSlot(1)
        if (outStack.isEmpty) return

        val result = FluidUtil.tryFillContainer(outStack, getFluidHandler(), Int.MAX_VALUE, null, true)
        if (!result.isSuccess) return

        val filled = result.result
        if (filled == outStack) return

        containerSlots.setStackInSlot(1, filled)
        markDirty()
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        compound.setTag("Storage", storage.writeNBT(NBTTagCompound()))
        compound.setBoolean("AutoInput", autoInput)
        compound.setBoolean("AutoOutput", autoOutput)
        compound.setLong("MaxInRate", maxInputRate)
        compound.setLong("MaxOutRate", maxOutputRate)
        compound.setTag("ContainerSlots", containerSlots.serializeNBT())
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
        maxInputRate = if (compound.hasKey("MaxInRate")) compound.getLong("MaxInRate") else 0L
        maxOutputRate = if (compound.hasKey("MaxOutRate")) compound.getLong("MaxOutRate") else 0L

        val tierLevel = if (compound.hasKey("Tier")) compound.getInteger("Tier") else config.tier.tier
        val hatchType = runCatching {
            if (compound.hasKey("HatchType")) HatchType.valueOf(compound.getString("HatchType")) else config.hatchType
        }.getOrElse { config.hatchType }

        config = HatchConfigRegistry.getFluidHatchConfig(tierLevel, hatchType)
        storage = createStorage(config)

        if (compound.hasKey("Storage")) {
            storage.readNBT(compound.getCompoundTag("Storage"))
        }

        if (compound.hasKey("ContainerSlots")) {
            containerSlots.deserializeNBT(compound.getCompoundTag("ContainerSlots"))
        }
    }

    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return true
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true
        }
        return super.hasCapability(capability, facing)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return fluidHandler as T
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return containerSlots as T
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
