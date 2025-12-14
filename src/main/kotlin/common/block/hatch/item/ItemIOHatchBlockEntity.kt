package github.kasuminova.prototypemachinery.common.block.hatch.item

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.impl.storage.ItemResourceStorage
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.items.CapabilityItemHandler
import net.minecraftforge.items.IItemHandler

/**
 * # ItemIOHatchBlockEntity - Item IO Hatch Block Entity
 * # ItemIOHatchBlockEntity - 物品交互仓方块实体
 *
 * Block entity that provides separate input and output item storage functionality.
 *
 * 提供独立输入和输出物品存储功能的方块实体。
 *
 * @param config The configuration for this IO hatch
 */
public class ItemIOHatchBlockEntity(
    public var config: ItemIOHatchConfig
) : BlockEntity(), IGuiHolder<PosGuiData> {

    // Primary constructor for NBT deserialization
    public constructor() : this(ItemIOHatchConfig.createDefault(1))

    /**
     * The input item storage.
     * 输入物品存储。
     */
    public var inputStorage: ItemResourceStorage = createInputStorage(config)

    /**
     * The output item storage.
     * 输出物品存储。
     */
    public var outputStorage: ItemResourceStorage = createOutputStorage(config)

    private fun createInputStorage(config: ItemIOHatchConfig): ItemResourceStorage {
        return ItemResourceStorage(
            maxTypes = config.inputSlotCount,
            maxCountPerType = config.inputMaxStackSize
        )
    }

    private fun createOutputStorage(config: ItemIOHatchConfig): ItemResourceStorage {
        return ItemResourceStorage(
            maxTypes = config.outputSlotCount,
            maxCountPerType = config.outputMaxStackSize
        )
    }

    /**
     * Applies a new config to this IO hatch at runtime.
     * Rebuilds both storages and migrates existing contents.
     */
    public fun applyConfig(newConfig: ItemIOHatchConfig) {
        if (this.config == newConfig) return

        val oldInput = inputStorage
        val oldOutput = outputStorage

        val newInput = createInputStorage(newConfig)
        val newOutput = createOutputStorage(newConfig)

        for (slot in 0 until oldInput.slotCount) {
            val key = oldInput.getSlot(slot) ?: continue
            val amount = oldInput.extractFromSlot(slot, Long.MAX_VALUE, true)
            if (amount > 0L) newInput.insert(key, amount, false)
        }
        for (slot in 0 until oldOutput.slotCount) {
            val key = oldOutput.getSlot(slot) ?: continue
            val amount = oldOutput.extractFromSlot(slot, Long.MAX_VALUE, true)
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
    private val inputHandler: ItemIOHatchInputHandler by lazy { ItemIOHatchInputHandler(this) }
    private val outputHandler: ItemIOHatchOutputHandler by lazy { ItemIOHatchOutputHandler(this) }
    private val combinedHandler: ItemIOHatchCombinedHandler by lazy { ItemIOHatchCombinedHandler(this) }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        compound.setTag("InputStorage", inputStorage.writeNBT(NBTTagCompound()))
        compound.setTag("OutputStorage", outputStorage.writeNBT(NBTTagCompound()))
        compound.setBoolean("AutoInput", autoInput)
        compound.setBoolean("AutoOutput", autoOutput)
        compound.setInteger("Tier", config.tier.tier)
        compound.setInteger("InputSlotCount", config.inputSlotCount)
        compound.setInteger("OutputSlotCount", config.outputSlotCount)
        compound.setLong("InputMaxStackSize", config.inputMaxStackSize)
        compound.setLong("OutputMaxStackSize", config.outputMaxStackSize)
        return compound
    }

    override fun readFromNBT(compound: NBTTagCompound) {
        super.readFromNBT(compound)
        autoInput = compound.getBoolean("AutoInput")
        autoOutput = compound.getBoolean("AutoOutput")

        val tierLevel = if (compound.hasKey("Tier")) compound.getInteger("Tier") else config.tier.tier
        config = HatchConfigRegistry.getItemIOHatchConfig(tierLevel)
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
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true
        }
        return super.hasCapability(capability, facing)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            // Return combined handler by default, can be customized based on facing
            return combinedHandler as T
        }
        return super.getCapability(capability, facing)
    }

    /**
     * Gets the input handler for external use.
     * 获取用于外部使用的输入处理器。
     */
    public fun getInputHandler(): IItemHandler = inputHandler

    /**
     * Gets the output handler for external use.
     * 获取用于外部使用的输出处理器。
     */
    public fun getOutputHandler(): IItemHandler = outputHandler

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        return ItemIOHatchGUI.buildPanel(this, data, syncManager)
    }

}
