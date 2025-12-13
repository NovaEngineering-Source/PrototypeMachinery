package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.impl.storage.FluidResourceStorage
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.fluids.capability.CapabilityFluidHandler
import net.minecraftforge.fluids.capability.IFluidHandler

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
) : BlockEntity(), IGuiHolder<PosGuiData> {

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

    // Capability wrappers
    private val inputHandler: FluidIOHatchInputHandler by lazy { FluidIOHatchInputHandler(this) }
    private val outputHandler: FluidIOHatchOutputHandler by lazy { FluidIOHatchOutputHandler(this) }
    private val combinedHandler: FluidIOHatchCombinedHandler by lazy { FluidIOHatchCombinedHandler(this) }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        compound.setTag("InputStorage", inputStorage.writeNBT(NBTTagCompound()))
        compound.setTag("OutputStorage", outputStorage.writeNBT(NBTTagCompound()))
        compound.setBoolean("AutoInput", autoInput)
        compound.setBoolean("AutoOutput", autoOutput)
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
            return combinedHandler as T
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

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        return FluidIOHatchGUI.buildPanel(this, data, syncManager)
    }

}
