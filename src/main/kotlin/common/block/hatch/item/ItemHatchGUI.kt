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
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import net.minecraft.item.ItemStack
import java.util.function.BooleanSupplier

/**
 * # ItemHatchGUI - Item Hatch GUI Builder
 * # ItemHatchGUI - 物品仓 GUI 构建器
 *
 * Builds the ModularUI panel for item hatches.
 *
 * 为物品仓构建 ModularUI 面板。
 */
public object ItemHatchGUI {

    private val uiTextures: UITextures = UITextures()

    // GUI dimensions (base size for tiers 1-4)
    private const val GUI_WIDTH_SMALL = 176
    private const val GUI_HEIGHT_SMALL = 166

    // Large GUI size for tiers 5-10 (512x512 textures)
    private const val GUI_WIDTH_LARGE = 352
    private const val GUI_HEIGHT_LARGE = 332

    // Slot grid configuration
    private const val SLOT_SIZE = 18
    private const val SLOTS_PER_ROW = 9
    private const val MAX_VISIBLE_ROWS = 4

    private fun getGuiSize(tier: HatchTier): Pair<Int, Int> {
        return if (tier.tier <= 4) {
            Pair(GUI_WIDTH_SMALL, GUI_HEIGHT_SMALL)
        } else {
            Pair(GUI_WIDTH_LARGE, GUI_HEIGHT_LARGE)
        }
    }

    /**
     * Builds the GUI panel for an item hatch.
     * 为物品仓构建 GUI 面板。
     */
    public fun buildPanel(
        hatch: ItemHatchBlockEntity,
        data: PosGuiData,
        syncManager: PanelSyncManager
    ): ModularPanel {
        val config = hatch.config
        val tier = config.tier
        val hatchType = config.hatchType

        val (guiWidth, guiHeight) = getGuiSize(tier)

        // Select background texture based on tier
        val backgroundTexture = getBackgroundTexture(tier, hatchType)

        val panel = ModularPanel.defaultPanel("item_hatch_${hatchType.name.lowercase()}_${tier.tier}")
            .size(guiWidth, guiHeight)
            .background(backgroundTexture)

        // Title bar
        panel.child(
            IKey.lang(hatch.blockType.translationKey + ".name")
                .asWidget()
                .pos(8, 6)
        )

        // Main content area
        panel.child(buildMainContent(hatch, syncManager, config))

        // Player inventory
        panel.child(
            SlotGroupWidget.playerInventory(true)
                .pos(8, guiHeight - 82 - 4)
        )

        // Auto-input/output buttons
        panel.child(buildControlButtons(hatch, syncManager, hatchType, guiWidth))

        return panel
    }

    private fun buildMainContent(
        hatch: ItemHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: ItemHatchConfig
    ): IWidget {
        val slotCount = config.slotCount
        val canInsert = config.hatchType != HatchType.OUTPUT
        val canExtract = config.hatchType != HatchType.INPUT

        // Register sync handler for storage
        val storageSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>> = ResourceSlotSyncHandler(
            hatch.storage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMItemKeyType.readNBT(nbt) as? PMKey<ItemStack> }
        )
        syncManager.syncValue("storage", storageSyncHandler)

        val column = Column()
            .pos(8, 18)
            .widthRel(1f)

        var slotIndex = 0
        while (slotIndex < slotCount) {
            val row = Row().height(SLOT_SIZE)
            for (col in 0 until SLOTS_PER_ROW) {
                if (slotIndex >= slotCount) break
                row.child(
                    ResourceSlotWidget<PMKey<ItemStack>>()
                        .syncHandler(storageSyncHandler)
                        .slotIndex(slotIndex)
                        .slotBackground(uiTextures.defaultSlotBackground)
                        .canInsert(canInsert)
                        .canExtract(canExtract)
                )
                slotIndex++
            }
            column.child(row)
        }

        return column
    }

    private fun buildControlButtons(
        hatch: ItemHatchBlockEntity,
        syncManager: PanelSyncManager,
        hatchType: HatchType,
        guiWidth: Int
    ): IWidget {
        val buttonRow = Row()
        buttonRow.pos(guiWidth - 40, 4)
        buttonRow.height(16)

        // Auto-input button (only for INPUT and IO)
        if (hatchType != HatchType.OUTPUT) {
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
        }

        // Auto-output button (only for OUTPUT and IO)
        if (hatchType != HatchType.INPUT) {
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
        }

        return buttonRow
    }

    private fun getBackgroundTexture(tier: HatchTier, hatchType: HatchType): IDrawable {
        val typeName = when (hatchType) {
            HatchType.INPUT, HatchType.OUTPUT -> "gui_item_hatch"
            HatchType.IO -> "gui_item_io_hatch"
        }
        val tierLevel = tier.tier
        val fileName = "${typeName}_${tierLevel}.png"

        return UITexture.fullImage(
            PrototypeMachinery.MOD_ID,
            "textures/gui/$typeName/$fileName"
        )
    }

}
