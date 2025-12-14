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
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import net.minecraft.network.PacketBuffer
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack
import java.util.function.BooleanSupplier

/**
 * # FluidIOHatchGUI - Fluid IO Hatch GUI Builder
 * # FluidIOHatchGUI - 流体交互仓 GUI 构建器
 *
 * Builds the ModularUI panel for fluid IO hatches with separate input/output sections.
 *
 * 为流体交互仓构建具有独立输入/输出区域的 ModularUI 面板。
 */
public object FluidIOHatchGUI {

    private val HIDE_PLAYER_INV_BG: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

    private const val GUI_W = 195
    private const val GUI_H = 194

    private const val AUTO_BTN_W = 14
    private const val AUTO_BTN_H = 14

    // Same positions as item/fluid hatch
    private const val AUTO_INPUT_X = 4
    private const val AUTO_INPUT_Y = 13
    private const val AUTO_OUTPUT_X = 4
    private const val AUTO_OUTPUT_Y = 31
    private const val CLEAR_X = 4
    private const val CLEAR_Y = 49

    // Fluid bars
    private const val BAR_W = 16
    private const val BAR_H = 66

    // Output bar (internal output -> player)
    private const val OUT_BAR_X = 169
    private const val OUT_BAR_Y = 10

    // Input bar (player -> internal input)
    private const val IN_BAR_X = 142
    private const val IN_BAR_Y = 36

    // Gauge overlay size is 16x68, y = barY - 1
    private const val GAUGE_W = 18
    private const val GAUGE_H = 68

    // Container slots (only 2)
    // Slot 0: drain container -> internal input
    private const val CONTAINER_DRAIN_X = 141
    private const val CONTAINER_DRAIN_Y = 9

    // Slot 1: fill container <- internal output
    private const val CONTAINER_FILL_X = 168
    private const val CONTAINER_FILL_Y = 85

    // Rate text fields
    private const val RATE_IN_X = 40
    private const val RATE_IN_Y = 82
    private const val RATE_OUT_X = 40
    private const val RATE_OUT_Y = 93
    private const val RATE_W = 93
    private const val RATE_H = 11

    private const val OUT_FLUID_NAME_X = 40
    private const val OUT_FLUID_NAME_Y = 54
    private const val OUT_FLUID_AMOUNT_X = 40
    private const val OUT_FLUID_AMOUNT_Y = 66

    private const val IN_FLUID_NAME_X = 40
    private const val IN_FLUID_NAME_Y = 25
    private const val IN_FLUID_AMOUNT_X = 40
    private const val IN_FLUID_AMOUNT_Y = 37

    private const val FLUID_LABEL_W = 93
    private const val FLUID_LABEL_H = 10

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

    private val AUTO_OUTPUT_OFF: IDrawable = stateIcon(185, 1)
    private val AUTO_OUTPUT_HOVER: IDrawable = stateIcon(199, 1)
    private val AUTO_OUTPUT_ON: IDrawable = stateIcon(213, 1)

    private val CLEAR_OFF: IDrawable = stateIcon(185, 35)
    private val CLEAR_HOVER: IDrawable = stateIcon(199, 35)
    private val CLEAR_PRESSED: IDrawable = stateIcon(213, 35)

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

    private class FluidIOHatchActionSyncHandler(
        private val hatch: FluidIOHatchBlockEntity
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
     * Builds the GUI panel for a fluid IO hatch.
     * 为流体交互仓构建 GUI 面板。
     */
    public fun buildPanel(
        hatch: FluidIOHatchBlockEntity,
        data: PosGuiData,
        syncManager: PanelSyncManager
    ): ModularPanel {
        val config = hatch.config
        val tier = config.tier

        val backgroundTexture = getBackgroundTexture(tier)

        val actionHandler = FluidIOHatchActionSyncHandler(hatch)
        syncManager.syncValue("actions", actionHandler)

        val panel = ModularPanel.defaultPanel("fluid_io_hatch_${tier.tier}")
            .size(GUI_W, GUI_H)
            .background(backgroundTexture)

        val inTotalCapacity = config.inputTankCapacity * config.inputTankCount.toLong()
        val outTotalCapacity = config.outputTankCapacity * config.outputTankCount.toLong()

        // Title bar
        panel.child(
            IKey.lang(hatch.blockType.translationKey + ".name")
                .asWidget()
                .pos(65, 7)
        )

        // Fluid name / amount labels
        panel.child(
            IKey.dynamic {
                val fluid = hatch.outputStorage.getFluid()
                if (fluid == null || hatch.outputStorage.getTotalAmount() <= 0L) "-" else fluid.localizedName
            }
                .asWidget()
                .alignment(Alignment.CenterLeft)
                .pos(OUT_FLUID_NAME_X, OUT_FLUID_NAME_Y)
                .size(FLUID_LABEL_W, FLUID_LABEL_H)
                // Match TextFieldWidget default padding(4, 0)
                .apply { area.padding.all(4, 0) }
        )
        panel.child(
            IKey.dynamic { formatMbLabel(hatch.outputStorage.getTotalAmount(), outTotalCapacity) }
                .asWidget()
                .alignment(Alignment.CenterLeft)
                .pos(OUT_FLUID_AMOUNT_X, OUT_FLUID_AMOUNT_Y)
                .size(FLUID_LABEL_W, FLUID_LABEL_H)
                // Match TextFieldWidget default padding(4, 0)
                .apply { area.padding.all(4, 0) }
        )
        panel.child(
            IKey.dynamic {
                val fluid = hatch.inputStorage.getFluid()
                if (fluid == null || hatch.inputStorage.getTotalAmount() <= 0L) "-" else fluid.localizedName
            }
                .asWidget()
                .alignment(Alignment.CenterLeft)
                .pos(IN_FLUID_NAME_X, IN_FLUID_NAME_Y)
                .size(FLUID_LABEL_W, FLUID_LABEL_H)
                // Match TextFieldWidget default padding(4, 0)
                .apply { area.padding.all(4, 0) }
        )
        panel.child(
            IKey.dynamic { formatMbLabel(hatch.inputStorage.getTotalAmount(), inTotalCapacity) }
                .asWidget()
                .alignment(Alignment.CenterLeft)
                .pos(IN_FLUID_AMOUNT_X, IN_FLUID_AMOUNT_Y)
                .size(FLUID_LABEL_W, FLUID_LABEL_H)
                // Match TextFieldWidget default padding(4, 0)
                .apply { area.padding.all(4, 0) }
        )

        // Sync storages for client rendering
        val inputSync: ResourceStorageSyncHandler<PMKey<FluidStack>> = ResourceStorageSyncHandler(
            hatch.inputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMFluidKeyType.readNBT(nbt) as? PMKey<FluidStack> }
        )
        val outputSync: ResourceStorageSyncHandler<PMKey<FluidStack>> = ResourceStorageSyncHandler(
            hatch.outputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMFluidKeyType.readNBT(nbt) as? PMKey<FluidStack> }
        )
        syncManager.syncValue("inputStorage", inputSync)
        syncManager.syncValue("outputStorage", outputSync)

        // Output fluid bar
        panel.child(
            FluidBarWidget(
                fluidSupplier = { hatch.outputStorage.getFluid() },
                amountSupplier = { hatch.outputStorage.getTotalAmount() },
                capacitySupplier = { outTotalCapacity }
            )
                .pos(OUT_BAR_X, OUT_BAR_Y)
                .size(BAR_W, BAR_H)
        )
        panel.child(
            gaugeOverlay(tier)
                .asWidget()
                .pos(OUT_BAR_X - 1, OUT_BAR_Y - 1)
                .size(GAUGE_W, GAUGE_H)
                // NOTE: the overlay is above the actual FluidBarWidget, so it receives hover events.
                .tooltipDynamic { tooltip ->
                    val amount = hatch.outputStorage.getTotalAmount().coerceAtLeast(0L)
                    val fluid = hatch.outputStorage.getFluid()
                    val name = if (fluid == null || amount <= 0L) "-" else fluid.localizedName
                    tooltip.addLine(IKey.str(name))
                    tooltip.addLine(IKey.str(NumberFormatUtil.formatMbPairGrouped(amount, outTotalCapacity)))
                }
                .tooltipShowUpTimer(0)
                .tooltipAutoUpdate(true)
        )

        // Input fluid bar
        panel.child(
            FluidBarWidget(
                fluidSupplier = { hatch.inputStorage.getFluid() },
                amountSupplier = { hatch.inputStorage.getTotalAmount() },
                capacitySupplier = { inTotalCapacity }
            )
                .pos(IN_BAR_X, IN_BAR_Y)
                .size(BAR_W, BAR_H)
        )
        panel.child(
            gaugeOverlay(tier)
                .asWidget()
                .pos(IN_BAR_X - 1, IN_BAR_Y - 1)
                .size(GAUGE_W, GAUGE_H)
                // NOTE: the overlay is above the actual FluidBarWidget, so it receives hover events.
                .tooltipDynamic { tooltip ->
                    val amount = hatch.inputStorage.getTotalAmount().coerceAtLeast(0L)
                    val fluid = hatch.inputStorage.getFluid()
                    val name = if (fluid == null || amount <= 0L) "-" else fluid.localizedName
                    tooltip.addLine(IKey.str(name))
                    tooltip.addLine(IKey.str(NumberFormatUtil.formatMbPairGrouped(amount, inTotalCapacity)))
                }
                .tooltipShowUpTimer(0)
                .tooltipAutoUpdate(true)
        )

        // Container slots (2 slots)
        panel.child(
            ItemSlot()
                .slot(hatch.getContainerSlots(), 0)
                .background(IDrawable.EMPTY)
                .pos(CONTAINER_DRAIN_X, CONTAINER_DRAIN_Y)
        )
        panel.child(
            ItemSlot()
                .slot(hatch.getContainerSlots(), 1)
                .background(IDrawable.EMPTY)
                .pos(CONTAINER_FILL_X, CONTAINER_FILL_Y)
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

        // Control buttons
        panel.child(buildControlButtons(hatch, actionHandler))

        return panel
    }

    private fun buildControlButtons(
        hatch: FluidIOHatchBlockEntity,
        actionHandler: FluidIOHatchActionSyncHandler
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
        val autoOutputSync = BooleanSyncValue(
            BooleanSupplier { hatch.autoOutput },
            BooleanConsumer { hatch.autoOutput = it }
        )

        container.child(
            createAutoToggle(isInput = true, value = autoInputSync)
                .pos(AUTO_INPUT_X, AUTO_INPUT_Y)
                .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_input")) }
        )
        container.child(
            createAutoToggle(isInput = false, value = autoOutputSync)
                .pos(AUTO_OUTPUT_X, AUTO_OUTPUT_Y)
                .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_output")) }
        )

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

    private fun getBackgroundTexture(tier: HatchTier): IDrawable {
        val tierLevel = tier.tier
        val fileName = "gui_fluid_io_hatch_${tierLevel}.png"

        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/gui_fluid_hatch/$fileName"))
            .imageSize(256, 256)
            .subAreaXYWH(0, 0, GUI_W, GUI_H)
            .build()
    }

}
