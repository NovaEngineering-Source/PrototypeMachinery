package github.kasuminova.prototypemachinery.common.block.hatch.fluid

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.utils.BooleanConsumer
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.value.sync.StringSyncValue
import com.cleanroommc.modularui.value.sync.SyncHandler
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.SlotGroupWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.slot.ItemSlot
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.client.gui.sync.ResourceStorageSyncHandler
import github.kasuminova.prototypemachinery.client.gui.util.NumberFormatUtil
import github.kasuminova.prototypemachinery.client.gui.widget.FluidBarWidget
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import net.minecraft.network.PacketBuffer
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import java.util.function.BooleanSupplier

/**
 * # FluidHatchGUI - Fluid Hatch GUI Builder
 * # FluidHatchGUI - 流体仓 GUI 构建器
 *
 * Builds the ModularUI panel for fluid hatches.
 *
 * 为流体仓构建 ModularUI 面板。
 */
public object FluidHatchGUI {

    private const val GUI_W = 195
    private const val GUI_H = 194

    private const val AUTO_BTN_W = 14
    private const val AUTO_BTN_H = 14

    // Same positions as item hatch
    private const val AUTO_INPUT_X = 4
    private const val AUTO_INPUT_Y = 13
    private const val AUTO_OUTPUT_X = 4
    private const val AUTO_OUTPUT_Y = 31
    private const val CLEAR_X = 4
    private const val CLEAR_Y = 49

    private const val FLUID_BAR_X = 169
    private const val FLUID_BAR_Y = 10
    private const val FLUID_BAR_W = 16
    private const val FLUID_BAR_H = 66

    private const val GAUGE_X = FLUID_BAR_X - 1
    private const val GAUGE_Y = 9
    private const val GAUGE_W = 18
    private const val GAUGE_H = 68

    private const val CONTAINER_IN_X = 136
    private const val CONTAINER_IN_Y = 9
    private const val CONTAINER_OUT_X = 136
    private const val CONTAINER_OUT_Y = 59

    private const val RATE_IN_X = 40
    private const val RATE_IN_Y = 56
    private const val RATE_OUT_X = 40
    private const val RATE_OUT_Y = 67
    private const val RATE_W = 88
    private const val RATE_H = 11

    private const val FLUID_NAME_X = 40
    private const val FLUID_NAME_Y = 24
    private const val FLUID_AMOUNT_X = 40
    private const val FLUID_AMOUNT_Y = 40
    private const val FLUID_LABEL_W = 88
    private const val FLUID_LABEL_H = 10

    private val HIDE_PLAYER_INV_BG: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

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

    private val CLEAR_OFF: IDrawable = stateIcon(185, 35)
    private val CLEAR_HOVER: IDrawable = stateIcon(199, 35)
    private val CLEAR_PRESSED: IDrawable = stateIcon(213, 35)
    private val CLEAR_DISABLED: IDrawable = stateIcon(227, 35)

    private fun gaugeOverlay(tier: HatchTier): IDrawable {
        val y = when (tier.tier) {
            9 -> 98
            10 -> 167
            else -> 29
        }
        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
            .imageSize(256, 256)
            .subAreaXYWH(73, y, GAUGE_W, GAUGE_H)
            .build()
    }

    private fun formatMbLabel(amount: Long, capacity: Long): String {
        return NumberFormatUtil.formatMbPairCompact(amount, capacity)
    }

    private class FluidHatchActionSyncHandler(
        private val hatch: FluidHatchBlockEntity
    ) : SyncHandler() {
        private companion object {
            const val CLEAR = 1
        }

        fun requestClear() {
            syncToServer(CLEAR) { /* no payload */ }
        }

        override fun detectAndSendChanges(init: Boolean) {
            // no-op
        }

        override fun readOnClient(id: Int, buf: PacketBuffer) {
            // no-op
        }

        override fun readOnServer(id: Int, buf: PacketBuffer) {
            if (id == CLEAR) {
                hatch.clearFluids()
            }
        }
    }

    /**
     * Builds the GUI panel for a fluid hatch.
     * 为流体仓构建 GUI 面板。
     */
    public fun buildPanel(
        hatch: FluidHatchBlockEntity,
        data: PosGuiData,
        syncManager: PanelSyncManager
    ): ModularPanel {
        val config = hatch.config
        val tier = config.tier
        val hatchType = config.hatchType

        val backgroundTexture = getBackgroundTexture(tier, hatchType)

        val actionHandler = FluidHatchActionSyncHandler(hatch)
        syncManager.syncValue("actions", actionHandler)

        val panel = ModularPanel.defaultPanel("fluid_hatch_${hatchType.name.lowercase()}_${tier.tier}")
            .size(GUI_W, GUI_H)
            .background(backgroundTexture)

        // Title bar
        panel.child(
            IKey.lang(hatch.blockType.translationKey + ".name")
                .asWidget()
                .pos(65, 7)
        )

        // Fluid name / amount labels
        val totalCapacity = config.tankCapacity * config.tankCount.toLong()
        panel.child(
            IKey.dynamic {
                val fluid = hatch.storage.getFluid()
                if (fluid == null || hatch.storage.getTotalAmount() <= 0L) "-" else fluid.localizedName
            }
                .asWidget()
                .alignment(Alignment.CenterLeft)
                .pos(FLUID_NAME_X, FLUID_NAME_Y)
                .size(FLUID_LABEL_W, FLUID_LABEL_H)
                // Match TextFieldWidget default padding(4, 0)
                .apply { getArea().getPadding().all(4, 0) }
        )
        panel.child(
            IKey.dynamic { formatMbLabel(hatch.storage.getTotalAmount(), totalCapacity) }
                .asWidget()
                .alignment(Alignment.CenterLeft)
                .pos(FLUID_AMOUNT_X, FLUID_AMOUNT_Y)
                .size(FLUID_LABEL_W, FLUID_LABEL_H)
                // Match TextFieldWidget default padding(4, 0)
                .apply { getArea().getPadding().all(4, 0) }
        )

        // Storage sync for client rendering
        val storageSync: ResourceStorageSyncHandler<PMKey<FluidStack>> = ResourceStorageSyncHandler(
            hatch.storage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMFluidKeyType.readNBT(nbt) as? PMKey<FluidStack> }
        )
        syncManager.syncValue("storage", storageSync)

        // Fluid bar
        panel.child(
            FluidBarWidget(
                fluidSupplier = { hatch.storage.getFluid() },
                amountSupplier = { hatch.storage.getTotalAmount() },
                capacitySupplier = { config.tankCapacity * config.tankCount.toLong() }
            )
                .pos(FLUID_BAR_X, FLUID_BAR_Y)
                .size(FLUID_BAR_W, FLUID_BAR_H)
        )

        // Gauge overlay on top of fluid bar
        panel.child(
            gaugeOverlay(tier)
                .asWidget()
                .pos(GAUGE_X, GAUGE_Y)
                .size(GAUGE_W, GAUGE_H)
                // NOTE: overlay is above the actual FluidBarWidget, so it receives hover events.
                .tooltipDynamic { tooltip ->
                    val amount = hatch.storage.getTotalAmount().coerceAtLeast(0L)
                    val fluid = hatch.storage.getFluid()
                    val name = if (fluid == null || amount <= 0L) "-" else fluid.localizedName

                    tooltip.addLine(IKey.str(name))
                    tooltip.addLine(IKey.str(NumberFormatUtil.formatMbPairGrouped(amount, totalCapacity)))
                }
                .tooltipShowUpTimer(0)
                .tooltipAutoUpdate(true)
        )

        // Container slots (2 slots, stack limit 1)
        panel.child(
            ItemSlot()
                .slot(hatch.getContainerSlots(), 0)
                .background(IDrawable.EMPTY)
                .pos(CONTAINER_IN_X, CONTAINER_IN_Y)
        )
        panel.child(
            ItemSlot()
                .slot(hatch.getContainerSlots(), 1)
                .background(IDrawable.EMPTY)
                .pos(CONTAINER_OUT_X, CONTAINER_OUT_Y)
        )

        // Max input/output rate text fields (no texture)
        val maxInSync = StringSyncValue(
            { hatch.maxInputRate.toString() },
            { s -> hatch.maxInputRate = s.toLongOrNull() ?: 0L }
        )
        val maxOutSync = StringSyncValue(
            { hatch.maxOutputRate.toString() },
            { s -> hatch.maxOutputRate = s.toLongOrNull() ?: 0L }
        )
        syncManager.syncValue("maxInRate", maxInSync)
        syncManager.syncValue("maxOutRate", maxOutSync)

        panel.child(
            TextFieldWidget()
                .syncHandler("maxInRate", 0)
                .setNumbersLong { it.coerceAtLeast(0L) }
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .pos(RATE_IN_X, RATE_IN_Y)
                .size(RATE_W, RATE_H)
        )
        panel.child(
            TextFieldWidget()
                .syncHandler("maxOutRate", 0)
                .setNumbersLong { it.coerceAtLeast(0L) }
                .background(IDrawable.EMPTY)
                .hoverBackground(IDrawable.EMPTY)
                .pos(RATE_OUT_X, RATE_OUT_Y)
                .size(RATE_W, RATE_H)
        )

        // Player inventory
        panel.child(
            SlotGroupWidget.playerInventory(HIDE_PLAYER_INV_BG)
                .pos(26, GUI_H - 81 - 4)
        )

        // Auto-input/output + clear buttons
        panel.child(buildControlButtons(hatch, syncManager, hatchType, actionHandler))

        return panel
    }

    private fun buildControlButtons(
        hatch: FluidHatchBlockEntity,
        syncManager: PanelSyncManager,
        hatchType: HatchType,
        actionHandler: FluidHatchActionSyncHandler
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

        fun createDisabledIcon(drawable: IDrawable, key: String): com.cleanroommc.modularui.widget.Widget<*> {
            return drawable.asWidget()
                .size(AUTO_BTN_W, AUTO_BTN_H)
                .tooltip { it.addLine(IKey.lang(key)) }
        }

        val container = Column().apply { pos(0, 0) }

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
            container.child(
                createDisabledIcon(AUTO_INPUT_DISABLED, "prototypemachinery.gui.hatch.auto_input")
                    .pos(AUTO_INPUT_X, AUTO_INPUT_Y)
            )
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
            container.child(
                createDisabledIcon(AUTO_OUTPUT_DISABLED, "prototypemachinery.gui.hatch.auto_output")
                    .pos(AUTO_OUTPUT_X, AUTO_OUTPUT_Y)
            )
        }

        // Clear button
        container.child(
            ButtonWidget()
                .size(AUTO_BTN_W, AUTO_BTN_H)
                .background(CLEAR_OFF)
                .hoverBackground(CLEAR_HOVER)
                .hoverOverlay(CLEAR_HOVER)
                .pos(CLEAR_X, CLEAR_Y)
                .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.clear")) }
                .onMousePressed { mouseButton: Int ->
                    if (mouseButton == 0 || mouseButton == 1) {
                        actionHandler.requestClear()
                        true
                    } else false
                }
        )

        return container
    }

    private fun getBackgroundTexture(tier: HatchTier, hatchType: HatchType): IDrawable {
        val tierLevel = tier.tier
        val fileName = "gui_fluid_hatch_${tierLevel}.png"

        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/gui_fluid_hatch/$fileName"))
            .imageSize(256, 256)
            .subAreaXYWH(0, 0, GUI_W, GUI_H)
            .build()
    }

}
