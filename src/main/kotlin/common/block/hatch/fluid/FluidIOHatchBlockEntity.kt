package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponent
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponentProvider
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.common.util.IOType
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureFluidStorageContainerComponent
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
 * # FluidIOHatchBlockEntity - Fluid IO Hatch Block Entity
 * # FluidIOHatchBlockEntity - 流体交互仓方块实体
 *
 * Block entity that provides separate input and output fluid storage functionality.
 *
 * 提供独立输入和输出流体存储功能的方块实体。
 *
 * @param config The configuration for this IO hatch
 */
public class FluidIOHatchBlockEntity(
    public var config: FluidIOHatchConfig
) : BlockEntity(), ITickable, IGuiHolder<PosGuiData>, StructureComponentProvider {

    // Primary constructor for NBT deserialization
    public constructor() : this(FluidIOHatchConfig.createDefault(1))

    /**
     * The input fluid storage.
     * 输入流体存储。
     */
    public var inputStorage: FluidResourceStorage = createInputStorage(config)

    /**
     * The output fluid storage.
     * 输出流体存储。
     */
    public var outputStorage: FluidResourceStorage = createOutputStorage(config)

    private fun createInputStorage(config: FluidIOHatchConfig): FluidResourceStorage {
        return FluidResourceStorage(
            maxTypes = config.inputTankCount,
            maxCountPerType = config.inputTankCapacity
        )
    }

    private fun createOutputStorage(config: FluidIOHatchConfig): FluidResourceStorage {
        return FluidResourceStorage(
            maxTypes = config.outputTankCount,
            maxCountPerType = config.outputTankCapacity
        )
    }

    /**
     * Applies a new config to this IO hatch at runtime.
     * Rebuilds both storages and migrates existing contents.
     */
    public fun applyConfig(newConfig: FluidIOHatchConfig) {
        if (this.config == newConfig) return

        val oldInput = inputStorage
        val oldOutput = outputStorage

        val newInput = createInputStorage(newConfig)
        val newOutput = createOutputStorage(newConfig)

        for (key in oldInput.getAllResources()) {
            val amount = oldInput.getAmount(key)
            if (amount > 0L) newInput.insert(key, amount, false)
        }
        for (key in oldOutput.getAllResources()) {
            val amount = oldOutput.getAmount(key)
            if (amount > 0L) newOutput.insert(key, amount, false)
        }

        this.config = newConfig
        this.inputStorage = newInput
        this.outputStorage = newOutput

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
            field = value
            markDirty()
            notifyClientUpdate()
        }

    /**
     * Max output rate (mB/t). 0 means unlimited.
     * 最大输出速率（mB/t）。0 表示不限制。
     */
    public var maxOutputRate: Long = 0L
        set(value) {
            field = value
            markDirty()
            notifyClientUpdate()
        }

    /**
     * 2-slot item handler for fluid containers.
     * Slot 0: drain container -> input storage
     * Slot 1: fill container <- output storage
     */
    private val containerSlots: ItemStackHandler = object : ItemStackHandler(2) {
        override fun getSlotLimit(slot: Int): Int = 1
    }

    public fun getContainerSlots(): ItemStackHandler = containerSlots

    public fun clearFluids() {
        inputStorage.clear()
        outputStorage.clear()
        markDirty()
        notifyClientUpdate()
    }

    // Capability wrappers
    private val inputHandler: FluidIOHatchInputHandler by lazy { FluidIOHatchInputHandler(this) }
    private val outputHandler: FluidIOHatchOutputHandler by lazy { FluidIOHatchOutputHandler(this) }
    private val combinedHandler: FluidIOHatchCombinedHandler by lazy { FluidIOHatchCombinedHandler(this) }

    override fun update() {
        val w = world ?: return
        if (w.isRemote) return

        tryDrainContainerToInput()
        tryFillContainerFromOutput()
    }

    private fun tryDrainContainerToInput() {
        val inStack = containerSlots.getStackInSlot(0)
        if (inStack.isEmpty) return

        val result = FluidUtil.tryEmptyContainer(inStack, getInputHandler(), Int.MAX_VALUE, null, true)
        if (!result.isSuccess) return

        val remaining = result.result
        if (remaining == inStack) return

        containerSlots.setStackInSlot(0, remaining)
        markDirty()
    }

    private fun tryFillContainerFromOutput() {
        val outStack = containerSlots.getStackInSlot(1)
        if (outStack.isEmpty) return

        val result = FluidUtil.tryFillContainer(outStack, getOutputHandler(), Int.MAX_VALUE, null, true)
        if (!result.isSuccess) return

        val filled = result.result
        if (filled == outStack) return

        containerSlots.setStackInSlot(1, filled)
        markDirty()
    }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        compound.setTag("InputStorage", inputStorage.writeNBT(NBTTagCompound()))
        compound.setTag("OutputStorage", outputStorage.writeNBT(NBTTagCompound()))
        compound.setBoolean("AutoInput", autoInput)
        compound.setBoolean("AutoOutput", autoOutput)
        compound.setLong("MaxInRate", maxInputRate)
        compound.setLong("MaxOutRate", maxOutputRate)
        compound.setTag("ContainerSlots", containerSlots.serializeNBT())
        compound.setInteger("Tier", config.tier.tier)
        compound.setInteger("InputTankCount", config.inputTankCount)
        compound.setInteger("OutputTankCount", config.outputTankCount)
        compound.setLong("InputTankCapacity", config.inputTankCapacity)
        compound.setLong("OutputTankCapacity", config.outputTankCapacity)
        return compound
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        autoInput = compound.getBoolean("AutoInput")
        autoOutput = compound.getBoolean("AutoOutput")
        maxInputRate = if (compound.hasKey("MaxInRate")) compound.getLong("MaxInRate") else 0L
        maxOutputRate = if (compound.hasKey("MaxOutRate")) compound.getLong("MaxOutRate") else 0L

        val tierLevel = if (compound.hasKey("Tier")) compound.getInteger("Tier") else config.tier.tier
        config = HatchConfigRegistry.getFluidIOHatchConfig(tierLevel)
        inputStorage = createInputStorage(config)
        outputStorage = createOutputStorage(config)

        if (compound.hasKey("InputStorage")) {
            inputStorage.readNBT(compound.getCompoundTag("InputStorage"))
        }
        if (compound.hasKey("OutputStorage")) {
            outputStorage.readNBT(compound.getCompoundTag("OutputStorage"))
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
            return combinedHandler as T
        }
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return containerSlots as T
        }
        return super.getCapability(capability, facing)
    }

    /**
     * Gets the input handler for external use.
     * 获取用于外部使用的输入处理器。
     */
    public fun getInputHandler(): IFluidHandler = inputHandler

    /**
     * Gets the output handler for external use.
     * 获取用于外部使用的输出处理器。
     */
    public fun getOutputHandler(): IFluidHandler = outputHandler

    public fun getCombinedHandler(): IFluidHandler = combinedHandler

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        return FluidIOHatchGUI.buildPanel(this, data, syncManager)
    }

    override fun createStructureComponents(machine: MachineInstance): Collection<StructureComponent> {
        return listOf(
            // inputStorage: outside fills -> machine drains -> IOType.OUTPUT
            StructureFluidStorageContainerComponent(owner = machine, provider = this, storage = inputStorage, allowed = setOf(IOType.OUTPUT)),
            // outputStorage: outside drains -> machine fills -> IOType.INPUT
            StructureFluidStorageContainerComponent(owner = machine, provider = this, storage = outputStorage, allowed = setOf(IOType.INPUT))
        )
    }

}
