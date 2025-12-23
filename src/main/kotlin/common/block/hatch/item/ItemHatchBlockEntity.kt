package github.kasuminova.prototypemachinery.common.block.hatch.item

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponent
import github.kasuminova.prototypemachinery.api.machine.component.StructureComponentProvider
import github.kasuminova.prototypemachinery.api.util.PortMode
import github.kasuminova.prototypemachinery.common.block.entity.BlockEntity
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.registry.HatchConfigRegistry
import github.kasuminova.prototypemachinery.impl.machine.component.container.StructureItemStorageContainerComponent
import github.kasuminova.prototypemachinery.impl.storage.ItemResourceStorage
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.world.World
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.items.CapabilityItemHandler

/**
 * # ItemHatchBlockEntity - Item Hatch Block Entity
 * # ItemHatchBlockEntity - 物品仓方块实体
 *
 * Block entity that provides item storage functionality.
 *
 * 提供物品存储功能的方块实体。
 *
 * @param config The configuration for this hatch
 */
public class ItemHatchBlockEntity(
    public var config: ItemHatchConfig
) : BlockEntity(), IGuiHolder<PosGuiData>, StructureComponentProvider {

    // Primary constructor for NBT deserialization
    public constructor() : this(ItemHatchConfig.createDefault(1, HatchType.INPUT))

    /**
     * The item storage.
     * 物品存储。
     */
    public var storage: ItemResourceStorage = createStorage(config)

    private fun createStorage(config: ItemHatchConfig): ItemResourceStorage {
        return ItemResourceStorage(
            maxTypes = config.slotCount,
            maxCountPerType = config.maxStackSize
        )
    }

    /**
     * Applies a new config to this hatch at runtime.
     *
     * Rebuilds the underlying storage with new limits and migrates existing contents.
     */
    public fun applyConfig(newConfig: ItemHatchConfig) {
        if (this.config == newConfig) return

        val oldStorage = storage
        val newStorage = createStorage(newConfig)

        // Migrate per-slot to avoid double-counting when the same key exists in multiple slots.
        for (slot in 0 until oldStorage.slotCount) {
            val key = oldStorage.getSlot(slot) ?: continue
            val amount = oldStorage.extractFromSlot(slot, Long.MAX_VALUE, true)
            if (amount > 0L) newStorage.insert(key, amount, false)
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
    private val itemHandler: ItemHatchItemHandler by lazy { ItemHatchItemHandler(this) }

    override fun writeToNBT(compound: NBTTagCompound): NBTTagCompound {
        super.writeToNBT(compound)
        compound.setTag("Storage", storage.writeNBT(NBTTagCompound()))
        compound.setBoolean("AutoInput", autoInput)
        compound.setBoolean("AutoOutput", autoOutput)
        compound.setInteger("Tier", config.tier.tier)
        compound.setString("HatchType", config.hatchType.name)
        compound.setInteger("SlotCount", config.slotCount)
        compound.setLong("MaxStackSize", config.maxStackSize)
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

        // Use current registry config for this tier/type.
        config = HatchConfigRegistry.getItemHatchConfig(tierLevel, hatchType)
        storage = createStorage(config)

        if (compound.hasKey("Storage")) {
            storage.readNBT(compound.getCompoundTag("Storage"))
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
            return itemHandler as T
        }
        return super.getCapability(capability, facing)
    }

    override fun buildUI(data: PosGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        return ItemHatchGUI.buildPanel(this, data, syncManager)
    }

    override fun createStructureComponents(machine: MachineInstance): Collection<StructureComponent> {
        val allowed = when (config.hatchType) {
            // NOTE: PortMode is from the recipe/machine perspective:
            // - sources (recipe inputs) must be EXTRACT-able => PortMode.OUTPUT
            // - targets (recipe outputs) must be INSERT-able => PortMode.INPUT
            // HatchType is from the external/world perspective:
            // - INPUT hatch: outside can insert -> machine should extract -> PortMode.OUTPUT
            // - OUTPUT hatch: outside can extract -> machine should insert -> PortMode.INPUT
            HatchType.INPUT -> setOf(PortMode.OUTPUT)
            HatchType.OUTPUT -> setOf(PortMode.INPUT)
            HatchType.IO -> setOf(PortMode.INPUT, PortMode.OUTPUT)
        }

        return listOf(
            StructureItemStorageContainerComponent(owner = machine, provider = this, storage = storage, allowed = allowed)
        )
    }

}
