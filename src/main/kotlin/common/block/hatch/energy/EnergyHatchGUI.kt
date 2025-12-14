package github.kasuminova.prototypemachinery.common.block.hatch.energy

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.utils.BooleanConsumer
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.value.sync.StringSyncValue
import com.cleanroommc.modularui.widgets.SlotGroupWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.gui.sync.EnergySyncHandler
import github.kasuminova.prototypemachinery.client.gui.util.NumberFormatUtil
import github.kasuminova.prototypemachinery.client.gui.widget.EnergyBarWidget
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import net.minecraft.util.ResourceLocation
import java.util.function.BooleanSupplier

/**
 * # EnergyHatchGUI - Energy Hatch GUI Builder
 * # EnergyHatchGUI - 能量仓 GUI 构建器
 *
 * Builds the ModularUI panel for energy hatches.
 *
 * 为能量仓构建 ModularUI 面板。
 */
public object EnergyHatchGUI {

    private val HIDE_PLAYER_INV_BG: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

    private const val GUI_W = 192
    private const val GUI_H = 168

    // Match FluidIOHatchGUI button positions/sizes
    private const val AUTO_BTN_W = 14
    private const val AUTO_BTN_H = 14
    private const val AUTO_INPUT_X = 4
    private const val AUTO_INPUT_Y = 13
    private const val AUTO_OUTPUT_X = 4
    private const val AUTO_OUTPUT_Y = 31

    // Energy bar (no background)
    private const val BAR_W = 16
    private const val BAR_H = 66
    private const val BAR_X = 146
    private const val BAR_Y = 10

    private const val ENERGY_AMOUNT_X = 40
    private const val ENERGY_AMOUNT_Y = 25

    private const val RATE_IN_X = 40
    private const val RATE_IN_Y = 52
    private const val RATE_OUT_X = 40
    private const val RATE_OUT_Y = 68
    private const val RATE_W = 97
    private const val RATE_H = 10

    private const val LABEL_W = 97
    private const val LABEL_H = 10

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

    private fun formatFePairCompact(amount: Long, capacity: Long): String {
        val a = amount.coerceAtLeast(0L).coerceAtMost(capacity.coerceAtLeast(0L))
        val c = capacity.coerceAtLeast(0L)
        return NumberFormatUtil.formatCompact(a) + " / " + NumberFormatUtil.formatCompact(c) + " FE"
    }

    /**
     * Builds the GUI panel for an energy hatch.
     * 为能量仓构建 GUI 面板。
     */
    public fun buildPanel(
        hatch: EnergyHatchBlockEntity,
        data: PosGuiData,
        syncManager: PanelSyncManager
    ): ModularPanel {
        val config = hatch.config
        val tier = config.tier
        val hatchType = config.hatchType

        val backgroundLocation = getBackgroundTextureLocation(tier, hatchType)
        val backgroundTexture = getBackgroundTexture(backgroundLocation)

        val panel = ModularPanel.defaultPanel("energy_hatch_${hatchType.name.lowercase()}_${tier.tier}")
            .size(GUI_W, GUI_H)
            .background(backgroundTexture)

        // Title bar
        panel.child(
            IKey.lang(hatch.blockType.translationKey + ".name")
                .asWidget()
                .pos(65, 7)
        )

        // Energy sync handler (server -> client)
        val energySyncHandler = EnergySyncHandler(hatch.storage)
        syncManager.syncValue("energy", energySyncHandler)

        // Energy bar (no background)
        panel.child(
            EnergyBarWidget(
                textureSupplier = { backgroundLocation },
                energySupplier = { hatch.storage.energy },
                capacitySupplier = { hatch.storage.capacity }
            )
                .pos(BAR_X, BAR_Y)
                .size(BAR_W, BAR_H)
        )

        // Energy amount label (same format as fluid labels)
        panel.child(
            IKey.dynamic { formatFePairCompact(hatch.storage.energy, hatch.storage.capacity) }
                .asWidget()
                .pos(ENERGY_AMOUNT_X, ENERGY_AMOUNT_Y)
                .size(LABEL_W, LABEL_H)
                // Match TextFieldWidget default padding(4, 0)
                .apply { area.padding.all(4, 0) }
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
        panel.child(buildControlButtons(hatch, hatchType))

        return panel
    }

    private fun buildControlButtons(
        hatch: EnergyHatchBlockEntity,
        hatchType: HatchType
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

    private fun getBackgroundTextureLocation(tier: HatchTier, hatchType: HatchType): ResourceLocation {
        val tierLevel = tier.tier
        val fileName = if (hatchType == HatchType.IO) {
            "gui_energy_io_hatch_${tierLevel}.png"
        } else {
            "gui_energy_hatch_${tierLevel}.png"
        }

        return ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/gui_energy_hatch/$fileName")
    }

    private fun getBackgroundTexture(location: ResourceLocation): IDrawable {
        return UITexture.builder()
            .location(location)
            .imageSize(256, 256)
            .subAreaXYWH(0, 0, GUI_W, GUI_H)
            .build()
    }

}
