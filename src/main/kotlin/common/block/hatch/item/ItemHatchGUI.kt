package github.kasuminova.prototypemachinery.common.block.hatch.item

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.api.widget.Interactable
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
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.client.gui.builder.UITextures
import github.kasuminova.prototypemachinery.client.gui.scroll.PMVerticalScrollData
import github.kasuminova.prototypemachinery.client.gui.sync.ResourceSlotSyncHandler
import github.kasuminova.prototypemachinery.client.gui.widget.InsetBorderWidget
import github.kasuminova.prototypemachinery.client.gui.widget.PMSmoothGrid
import github.kasuminova.prototypemachinery.client.gui.widget.ResourceSlotWidget
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKeyType
import net.minecraft.client.Minecraft
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
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

    // Derived from Python analysis of textures: non-edge-background bounding boxes.
    // NOTE: These sizes define the intended UI area in the texture starting at (0,0).
    private fun getTextureSpec(hatchType: HatchType, tier: HatchTier): GuiTextureSpec {
        val t = tier.tier
        return if (hatchType == HatchType.IO) {
            when (t) {
                1, 2 -> GuiTextureSpec(256, 251, 168)
                3, 4 -> GuiTextureSpec(256, 251, 186)
                5, 6, 7, 8, 9, 10 -> GuiTextureSpec(256, 256, 240)
                else -> GuiTextureSpec(256, 251, 225)
            }
        } else {
            when (t) {
                1, 2 -> GuiTextureSpec(256, 225, 168)
                3, 4 -> GuiTextureSpec(256, 243, 186)
                5, 6, 7, 8 -> GuiTextureSpec(256, 213, 239)
                9, 10 -> GuiTextureSpec(256, 213, 239)
                else -> GuiTextureSpec(256, 225, 225)
            }
        }
    }

    private val HIDE_PLAYER_INV_BG: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

    private const val SLOT_SIZE = 18

    private data class GridSpec(
        val columns: Int,
        val visibleRows: Int
    )

    private fun getGridSpec(tier: HatchTier): GridSpec {
        return when (tier.tier) {
            1, 2 -> GridSpec(columns = 8, visibleRows = 3)
            3, 4 -> GridSpec(columns = 9, visibleRows = 4)
            else -> GridSpec(columns = 9, visibleRows = 7)
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

        // Register sync handler for storage early so we can reuse it in multiple widgets.
        val storageSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>> = ResourceSlotSyncHandler(
            hatch.storage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMItemKeyType.readNBT(nbt) as? PMKey<ItemStack> }
        )
        syncManager.syncValue("storage", storageSyncHandler)

        val spec = getTextureSpec(hatchType, tier)
        val guiWidth = spec.uiWidth
        val guiHeight = spec.uiHeight

        // Select background texture based on tier
        val backgroundTexture = getBackgroundTexture(tier, hatchType, spec)

        val panel = ModularPanel.defaultPanel("item_hatch_${hatchType.name.lowercase()}_${tier.tier}")
            .size(guiWidth, guiHeight)
            .background(backgroundTexture)

        // Title bar
        panel.child(
            IKey.lang(hatch.blockType.translationKey + ".name")
                .asWidget()
                .pos(65, 7)
        )

        // Main content area
        panel.child(buildMainContent(hatch, config, tier, storageSyncHandler))

        // Slot grid inset border (foreground).
        val gridSpec = getGridSpec(tier)
        panel.child(
            InsetBorderWidget()
                .pos(26, 25)
                .size(gridSpec.columns * SLOT_SIZE, gridSpec.visibleRows * SLOT_SIZE)
        )

        // Player inventory (Shift-click -> quick insert into storage)
        panel.child(
            buildPlayerInventoryWithQuickInsert(storageSyncHandler)
                .pos(26, guiHeight - 81 - 4)
        )

        // Auto-input/output buttons
        panel.child(buildControlButtons(hatch, syncManager, hatchType, guiWidth))

        return panel
    }

    private fun buildMainContent(
        hatch: ItemHatchBlockEntity,
        config: ItemHatchConfig,
        tier: HatchTier,
        storageSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>>
    ): IWidget {
        val slotCount = config.slotCount
        val canInsert = config.hatchType != HatchType.OUTPUT
        // UX: allow players to click items out even from INPUT hatches.
        val canExtract = true

        val gridSpec = getGridSpec(tier)
        val totalRows = (slotCount + gridSpec.columns - 1) / gridSpec.columns

        val grid = PMSmoothGrid()
            .pos(26, 25)
            // Reserve space for the custom scrollbar strip so slots won't be clipped.
            .size(gridSpec.columns * SLOT_SIZE + PMVerticalScrollData.RESERVED_THICKNESS, gridSpec.visibleRows * SLOT_SIZE)

        if (totalRows > gridSpec.visibleRows) {
            grid.scrollable(PMVerticalScrollData().cancelScrollEdge(true))
        }

        grid.matrix(
            Grid.mapToMatrix(gridSpec.columns, slotCount) { index ->
                ResourceSlotWidget<PMKey<ItemStack>>()
                    .syncHandler(storageSyncHandler)
                    .slotIndex(index)
                    .slotBackground(ITEM_SLOT_BG)
                    .canInsert(canInsert)
                    .canExtract(canExtract)
            }
        )

        return grid
    }

    private fun buildPlayerInventoryWithQuickInsert(
        storageSyncHandler: ResourceSlotSyncHandler<PMKey<ItemStack>>
    ): SlotGroupWidget {
        val slotConsumer = HIDE_PLAYER_INV_BG
        val slotGroupWidget = SlotGroupWidget()
        slotGroupWidget.coverChildren()
        slotGroupWidget.name("player_inventory")

        val key = "player"

        // Hotbar (0..8)
        for (i in 0 until 9) {
            val invIndex = i
            val slot = object : com.cleanroommc.modularui.widgets.slot.ItemSlot() {
                override fun onMousePressed(mouseButton: Int): Interactable.Result {
                    if (Interactable.hasShiftDown()) {
                        val player = Minecraft.getMinecraft().player
                        if (player != null && player.inventory.itemStack.isEmpty) {
                            val stack = getSlot().stack
                            if (!stack.isEmpty) {
                                storageSyncHandler.requestQuickMoveInsertFromPlayerInventory(invIndex, mouseButton)
                                return Interactable.Result.SUCCESS
                            }
                        }
                    }
                    return super.onMousePressed(mouseButton)
                }
            }

            slotGroupWidget.child(
                slotConsumer.apply(invIndex, slot)
                    .syncHandler(key, invIndex)
                    .pos(i * 18, 3 * 18 + 4)
                    .name("slot_$invIndex")
            )
        }

        // Main inventory (9..35)
        for (i in 0 until 27) {
            val invIndex = i + 9
            val slot = object : com.cleanroommc.modularui.widgets.slot.ItemSlot() {
                override fun onMousePressed(mouseButton: Int): Interactable.Result {
                    if (Interactable.hasShiftDown()) {
                        val player = Minecraft.getMinecraft().player
                        if (player != null && player.inventory.itemStack.isEmpty) {
                            val stack = getSlot().stack
                            if (!stack.isEmpty) {
                                storageSyncHandler.requestQuickMoveInsertFromPlayerInventory(invIndex, mouseButton)
                                return Interactable.Result.SUCCESS
                            }
                        }
                    }
                    return super.onMousePressed(mouseButton)
                }
            }

            slotGroupWidget.child(
                slotConsumer.apply(invIndex, slot)
                    .syncHandler(key, invIndex)
                    .pos(i % 9 * 18, i / 9 * 18)
                    .name("slot_$invIndex")
            )
        }

        return slotGroupWidget
    }

    private fun buildControlButtons(
        hatch: ItemHatchBlockEntity,
        syncManager: PanelSyncManager,
        hatchType: HatchType,
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

        fun createDisabledIcon(isInput: Boolean): com.cleanroommc.modularui.widget.Widget<*> {
            val drawable = if (isInput) AUTO_INPUT_DISABLED else AUTO_OUTPUT_DISABLED
            val key = if (isInput) {
                "prototypemachinery.gui.hatch.auto_input"
            } else {
                "prototypemachinery.gui.hatch.auto_output"
            }
            return drawable.asWidget()
                .size(AUTO_BTN_W, AUTO_BTN_H)
                .tooltip { it.addLine(IKey.lang(key)) }
        }

        val container = Column().apply {
            pos(0, 0)
        }

        // Auto-input button: disabled on OUTPUT hatch
        if (hatchType != HatchType.OUTPUT) {
            val autoInputSync = BooleanSyncValue(
                BooleanSupplier { hatch.autoInput },
                BooleanConsumer { hatch.autoInput = it }
            )
            container.child(
                createAutoToggle(isInput = true, value = autoInputSync)
                    .pos(AUTO_INPUT_X, AUTO_INPUT_Y)
                    .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_input")) }
            )
        } else {
            container.child(createDisabledIcon(isInput = true).pos(AUTO_INPUT_X, AUTO_INPUT_Y))
        }

        // Auto-output button: disabled on INPUT hatch
        if (hatchType != HatchType.INPUT) {
            val autoOutputSync = BooleanSyncValue(
                BooleanSupplier { hatch.autoOutput },
                BooleanConsumer { hatch.autoOutput = it }
            )
            container.child(
                createAutoToggle(isInput = false, value = autoOutputSync)
                    .pos(AUTO_OUTPUT_X, AUTO_OUTPUT_Y)
                    .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_output")) }
            )
        } else {
            container.child(createDisabledIcon(isInput = false).pos(AUTO_OUTPUT_X, AUTO_OUTPUT_Y))
        }

        return container
    }

    private fun getBackgroundTexture(tier: HatchTier, hatchType: HatchType, spec: GuiTextureSpec): IDrawable {
        val typeName = when (hatchType) {
            HatchType.INPUT, HatchType.OUTPUT -> "gui_item_hatch"
            HatchType.IO -> "gui_item_io_hatch"
        }
        val tierLevel = tier.tier
        val fileName = "${typeName}_${tierLevel}.png"

        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/$typeName/$fileName"))
            .imageSize(spec.texSize, spec.texSize)
            .subAreaXYWH(0, 0, spec.uiWidth, spec.uiHeight)
            .build()
    }

}
