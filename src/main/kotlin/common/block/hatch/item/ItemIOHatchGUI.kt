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
import com.cleanroommc.modularui.widgets.layout.Grid
import com.cleanroommc.modularui.widgets.layout.Row
import github.kasuminova.prototypemachinery.client.gui.scroll.PMVerticalScrollData
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.client.gui.builder.UITextures
import github.kasuminova.prototypemachinery.client.gui.sync.ResourceSlotSyncHandler
import github.kasuminova.prototypemachinery.client.gui.widget.ResourceSlotWidget
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
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

    private val ITEM_SLOT_BG: IDrawable = UITexture.builder()
        .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
        .imageSize(256, 256)
        .subAreaXYWH(73, 0, 18, 18)
        .build()

    private const val AUTO_BTN_W = 14
    private const val AUTO_BTN_H = 14

    private const val AUTO_INPUT_X = 4
    private const val AUTO_INPUT_Y = 13
    private const val AUTO_OUTPUT_X = 4
    private const val AUTO_OUTPUT_Y = 31

    // states.png button sprites:
    // order: off / hover / enabled / disabled
    private fun stateIcon(x: Int, y: Int): IDrawable {
        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
            .imageSize(256, 256)
            .subAreaXYWH(x, y, AUTO_BTN_W, AUTO_BTN_H)
            .build()
    }

    private val AUTO_INPUT_OFF: IDrawable = stateIcon(185, 18)
    private val AUTO_INPUT_HOVER: IDrawable = stateIcon(199, 18)
    private val AUTO_INPUT_ON: IDrawable = stateIcon(213, 18)
    private val AUTO_INPUT_DISABLED: IDrawable = stateIcon(227, 18)

    private val AUTO_OUTPUT_OFF: IDrawable = stateIcon(185, 1)
    private val AUTO_OUTPUT_HOVER: IDrawable = stateIcon(199, 1)
    private val AUTO_OUTPUT_ON: IDrawable = stateIcon(213, 1)
    private val AUTO_OUTPUT_DISABLED: IDrawable = stateIcon(227, 1)

    private data class GuiTextureSpec(
        val texSize: Int,
        val uiWidth: Int,
        val uiHeight: Int
    )

    // Derived from Python analysis of textures (gui_item_io_hatch_*).
    private fun getTextureSpec(tier: HatchTier): GuiTextureSpec {
        return when (tier.tier) {
            1, 2 -> GuiTextureSpec(256, 251, 168)
            3, 4 -> GuiTextureSpec(256, 251, 186)
            5, 6, 7, 8, 9, 10 -> GuiTextureSpec(256, 245, 240)
            else -> GuiTextureSpec(256, 251, 225)
        }
    }

    private val HIDE_PLAYER_INV_BG: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

    private const val SLOT_SIZE = 18

    private data class GridSpec(
        val columns: Int,
        val visibleRows: Int,
        val inputX: Int,
        val inputY: Int,
        val outputX: Int,
        val outputY: Int
    )

    // Layout rules (slot size is fixed 18px):
    // - Input grid starts at X=26, Y=24
    // - Output grid X = inputX + (columns * SLOT_SIZE) + gap
    // - Gap inferred from Lv1-2: 114 - 26 - (4 * 18) = 16
    private fun getGridSpec(tier: HatchTier): GridSpec {
        val inputX = 26
        val inputY = 24
        val (cols, rows) = when (tier.tier) {
            1, 2 -> 4 to 3
            3, 4 -> 4 to 4
            else -> 5 to 7
        }

        // NOTE: Prefer explicit coordinates when provided (texture may contain extra decorative elements).
        val outputX = when (tier.tier) {
            1, 2 -> 114
            3, 4 -> 123
            else -> 137
        }
        val outputY = inputY
        return GridSpec(
            columns = cols,
            visibleRows = rows,
            inputX = inputX,
            inputY = inputY,
            outputX = outputX,
            outputY = outputY
        )
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

        val spec = getTextureSpec(tier)
        val guiWidth = spec.uiWidth
        val guiHeight = spec.uiHeight

        val gridSpec = getGridSpec(tier)

        // Select background texture based on tier
        val backgroundTexture = getBackgroundTexture(tier, spec)

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
                .pos(gridSpec.inputX, 18)
        )

        // Output section label
        panel.child(
            IKey.lang("prototypemachinery.gui.hatch.output")
                .asWidget()
                .pos(gridSpec.outputX, 18)
        )

        // Build input/output grids
        panel.child(buildInputSection(hatch, syncManager, config, gridSpec))
        panel.child(buildOutputSection(hatch, syncManager, config, gridSpec))

        // Player inventory
        panel.child(
            SlotGroupWidget.playerInventory(HIDE_PLAYER_INV_BG)
                .pos(26, guiHeight - 81 - 4)
        )

        // Control buttons
        panel.child(buildControlButtons(hatch, syncManager, guiWidth))

        return panel
    }

    private fun buildInputSection(
        hatch: ItemIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: ItemIOHatchConfig,
        gridSpec: GridSpec
    ): IWidget {
        val slotCount = config.inputSlotCount

        // Register sync handler for input storage
        val inputSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>> = ResourceSlotSyncHandler(
            hatch.inputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMItemKeyType.readNBT(nbt) as? PMKey<ItemStack> }
        )
        syncManager.syncValue("inputStorage", inputSyncHandler)

        return buildSlotGrid(
            syncHandler = inputSyncHandler,
            slotCount = slotCount,
            columns = gridSpec.columns,
            visibleRows = gridSpec.visibleRows,
            canInsert = true,
            canExtract = false
        ).apply {
            pos(gridSpec.inputX, gridSpec.inputY)
        }
    }

    private fun buildOutputSection(
        hatch: ItemIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: ItemIOHatchConfig,
        gridSpec: GridSpec
    ): IWidget {
        val slotCount = config.outputSlotCount

        // Register sync handler for output storage
        val outputSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>> = ResourceSlotSyncHandler(
            hatch.outputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMItemKeyType.readNBT(nbt) as? PMKey<ItemStack> }
        )
        syncManager.syncValue("outputStorage", outputSyncHandler)

        return buildSlotGrid(
            syncHandler = outputSyncHandler,
            slotCount = slotCount,
            columns = gridSpec.columns,
            visibleRows = gridSpec.visibleRows,
            canInsert = false,
            canExtract = true
        ).apply {
            pos(gridSpec.outputX, gridSpec.outputY)
        }
    }

    private fun buildSlotGrid(
        syncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>>,
        slotCount: Int,
        columns: Int,
        visibleRows: Int,
        canInsert: Boolean,
        canExtract: Boolean
    ): Grid {
        val totalRows = (slotCount + columns - 1) / columns

        return Grid().apply {
            // Reserve space for the custom scrollbar strip so slots won't be clipped.
            size(columns * SLOT_SIZE + PMVerticalScrollData.RESERVED_THICKNESS, visibleRows * SLOT_SIZE)
            // Requirement: all grids must be scrollable.
            scrollable(PMVerticalScrollData().cancelScrollEdge(true))

            matrix(
                Grid.mapToMatrix(columns, slotCount) { index ->
                    ResourceSlotWidget<PMKey<ItemStack>>()
                        .syncHandler(syncHandler)
                        .slotIndex(index)
                        .slotBackground(ITEM_SLOT_BG)
                        .canInsert(canInsert)
                        .canExtract(canExtract)
                }
            )
        }
    }

    private fun buildControlButtons(
        hatch: ItemIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        guiWidth: Int
    ): IWidget {
        fun createAutoToggle(isInput: Boolean, value: BooleanSyncValue): ToggleButton {
            val off = if (isInput) AUTO_INPUT_OFF else AUTO_OUTPUT_OFF
            val hover = if (isInput) AUTO_INPUT_HOVER else AUTO_OUTPUT_HOVER
            val on = if (isInput) AUTO_INPUT_ON else AUTO_OUTPUT_ON
            return ToggleButton()
                .value(value)
                .size(AUTO_BTN_W, AUTO_BTN_H)
                .background(false, off)
                .hoverBackground(false, hover)
                .background(true, on)
        }

        val container = Column().apply { pos(0, 0) }

        val autoInputSync = BooleanSyncValue(
            BooleanSupplier { hatch.autoInput },
            BooleanConsumer { hatch.autoInput = it }
        )
        container.child(
            createAutoToggle(isInput = true, value = autoInputSync)
                .pos(AUTO_INPUT_X, AUTO_INPUT_Y)
                .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_input")) }
        )

        val autoOutputSync = BooleanSyncValue(
            BooleanSupplier { hatch.autoOutput },
            BooleanConsumer { hatch.autoOutput = it }
        )
        container.child(
            createAutoToggle(isInput = false, value = autoOutputSync)
                .pos(AUTO_OUTPUT_X, AUTO_OUTPUT_Y)
                .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_output")) }
        )

        return container
    }

    private fun getBackgroundTexture(tier: HatchTier, spec: GuiTextureSpec): IDrawable {
        val tierLevel = tier.tier
        val fileName = "gui_item_io_hatch_${tierLevel}.png"

        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/gui_item_io_hatch/$fileName"))
            .imageSize(spec.texSize, spec.texSize)
            .subAreaXYWH(0, 0, spec.uiWidth, spec.uiHeight)
            .build()
    }

}
