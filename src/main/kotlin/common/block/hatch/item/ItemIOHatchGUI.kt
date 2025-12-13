package github.kasuminova.prototypemachinery.common.block.hatch.item

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.utils.BooleanConsumer
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.SlotGroupWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.layout.Row
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.client.gui.builder.UITextures
import github.kasuminova.prototypemachinery.client.gui.sync.ResourceSlotSyncHandler
import github.kasuminova.prototypemachinery.client.gui.widget.ResourceSlotWidget
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import net.minecraft.item.ItemStack
import java.util.function.BooleanSupplier

/**
 * # ItemIOHatchGUI - Item IO Hatch GUI Builder
 * # ItemIOHatchGUI - 物品交互仓 GUI 构建器
 *
 * Builds the ModularUI panel for item IO hatches with separate input/output sections.
 *
 * 为物品交互仓构建具有独立输入/输出区域的 ModularUI 面板。
 */
public object ItemIOHatchGUI {

    private val uiTextures: UITextures = UITextures()

    // GUI dimensions (base size for tiers 1-4)
    private const val GUI_WIDTH_SMALL = 192
    private const val GUI_HEIGHT_SMALL = 182

    // Large GUI size for tiers 5-10 (512x512 textures)
    private const val GUI_WIDTH_LARGE = 384
    private const val GUI_HEIGHT_LARGE = 364

    // Slot grid configuration
    private const val SLOT_SIZE = 18
    private const val SLOTS_PER_ROW = 5
    private const val MAX_VISIBLE_ROWS = 4

    // Section positions
    private const val INPUT_SECTION_X = 8
    private const val OUTPUT_SECTION_X = 100

    private fun getGuiSize(tier: HatchTier): Pair<Int, Int> {
        return if (tier.tier <= 4) {
            Pair(GUI_WIDTH_SMALL, GUI_HEIGHT_SMALL)
        } else {
            Pair(GUI_WIDTH_LARGE, GUI_HEIGHT_LARGE)
        }
    }

    /**
     * Builds the GUI panel for an item IO hatch.
     * 为物品交互仓构建 GUI 面板。
     */
    public fun buildPanel(
        hatch: ItemIOHatchBlockEntity,
        data: PosGuiData,
        syncManager: PanelSyncManager
    ): ModularPanel {
        val config = hatch.config
        val tier = config.tier

        val (guiWidth, guiHeight) = getGuiSize(tier)

        // Select background texture based on tier
        val backgroundTexture = getBackgroundTexture(tier)

        val panel = ModularPanel.defaultPanel("item_io_hatch_${tier.tier}")
            .size(guiWidth, guiHeight)
            .background(backgroundTexture)

        // Title bar
        panel.child(
            IKey.lang(hatch.blockType.translationKey + ".name")
                .asWidget()
                .pos(8, 6)
        )

        // Input section label
        panel.child(
            IKey.lang("prototypemachinery.gui.hatch.input")
                .asWidget()
                .pos(INPUT_SECTION_X, 18)
        )

        // Output section label
        panel.child(
            IKey.lang("prototypemachinery.gui.hatch.output")
                .asWidget()
                .pos(OUTPUT_SECTION_X, 18)
        )

        // Build input section
        panel.child(buildInputSection(hatch, syncManager, config))

        // Build output section
        panel.child(buildOutputSection(hatch, syncManager, config))

        // Player inventory
        panel.child(
            SlotGroupWidget.playerInventory(true)
                .pos(16, guiHeight - 82 - 4)
        )

        // Control buttons
        panel.child(buildControlButtons(hatch, syncManager, guiWidth))

        return panel
    }

    private fun buildInputSection(
        hatch: ItemIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: ItemIOHatchConfig
    ): Column {
        val slotCount = config.inputSlotCount

        // Register sync handler for input storage
        val inputSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>> = ResourceSlotSyncHandler(
            hatch.inputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMItemKeyType.readNBT(nbt) as? PMKey<ItemStack> }
        )
        syncManager.syncValue("inputStorage", inputSyncHandler)

        val container = Column()
        container.pos(INPUT_SECTION_X, 30)
        container.width(SLOT_SIZE * SLOTS_PER_ROW)
        container.height(SLOT_SIZE * MAX_VISIBLE_ROWS)

        buildSlotGrid(container, inputSyncHandler, slotCount, canInsert = true, canExtract = false)

        return container
    }

    private fun buildOutputSection(
        hatch: ItemIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: ItemIOHatchConfig
    ): Column {
        val slotCount = config.outputSlotCount

        // Register sync handler for output storage
        val outputSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>> = ResourceSlotSyncHandler(
            hatch.outputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMItemKeyType.readNBT(nbt) as? PMKey<ItemStack> }
        )
        syncManager.syncValue("outputStorage", outputSyncHandler)

        val container = Column()
        container.pos(OUTPUT_SECTION_X, 30)
        container.width(SLOT_SIZE * SLOTS_PER_ROW)
        container.height(SLOT_SIZE * MAX_VISIBLE_ROWS)

        buildSlotGrid(container, outputSyncHandler, slotCount, canInsert = false, canExtract = true)

        return container
    }

    private fun buildSlotGrid(
        container: Column,
        syncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>>,
        slotCount: Int,
        canInsert: Boolean,
        canExtract: Boolean
    ) {
        var slotIndex = 0
        while (slotIndex < slotCount) {
            val row = Row().height(SLOT_SIZE)
            for (col in 0 until SLOTS_PER_ROW) {
                if (slotIndex >= slotCount) break
                row.child(
                    ResourceSlotWidget<PMKey<ItemStack>>()
                        .syncHandler(syncHandler)
                        .slotIndex(slotIndex)
                        .slotBackground(uiTextures.defaultSlotBackground)
                        .canInsert(canInsert)
                        .canExtract(canExtract)
                )
                slotIndex++
            }
            container.child(row)
        }
    }

    private fun buildControlButtons(
        hatch: ItemIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        guiWidth: Int
    ): IWidget {
        val buttonRow = Row()
        buttonRow.pos(guiWidth - 40, 4)
        buttonRow.height(16)

        // Auto-input button
        val autoInputSync = BooleanSyncValue(
            BooleanSupplier { hatch.autoInput },
            BooleanConsumer { hatch.autoInput = it }
        )
        syncManager.syncValue("autoInput", autoInputSync)

        buttonRow.child(
            ToggleButton()
                .value(autoInputSync)
                .size(16, 16)
                .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_input")) }
        )

        // Auto-output button
        val autoOutputSync = BooleanSyncValue(
            BooleanSupplier { hatch.autoOutput },
            BooleanConsumer { hatch.autoOutput = it }
        )
        syncManager.syncValue("autoOutput", autoOutputSync)

        buttonRow.child(
            ToggleButton()
                .value(autoOutputSync)
                .size(16, 16)
                .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_output")) }
        )

        return buttonRow
    }

    private fun getBackgroundTexture(tier: HatchTier): IDrawable {
        val tierLevel = tier.tier
        val fileName = "gui_item_io_hatch_${tierLevel}.png"

        return UITexture.fullImage(
            PrototypeMachinery.MOD_ID,
            "textures/gui/gui_item_io_hatch/$fileName"
        )
    }

}
