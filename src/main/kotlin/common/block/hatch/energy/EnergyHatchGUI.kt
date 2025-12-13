package github.kasuminova.prototypemachinery.common.block.hatch.energy

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.factory.PosGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.utils.BooleanConsumer
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.ProgressWidget
import com.cleanroommc.modularui.widgets.SlotGroupWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import com.cleanroommc.modularui.widgets.layout.Row
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.gui.builder.UITextures
import github.kasuminova.prototypemachinery.client.gui.sync.EnergySyncHandler
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.common.util.NumberFormatter
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

    private val uiTextures: UITextures = UITextures()

    private data class GuiTextureSpec(
        val texSize: Int,
        val uiWidth: Int,
        val uiHeight: Int
    )

    // Derived from Python analysis of textures (gui_energy_hatch_*).
    private fun getTextureSpec(): GuiTextureSpec = GuiTextureSpec(256, 195, 256)

    private val HIDE_PLAYER_INV_BG: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

    // Energy bar configuration
    private const val ENERGY_BAR_WIDTH = 14
    private const val ENERGY_BAR_HEIGHT = 54
    private const val ENERGY_BAR_X = 80
    private const val ENERGY_BAR_Y = 20

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

        val spec = getTextureSpec()
        val guiWidth = spec.uiWidth
        val guiHeight = spec.uiHeight

        // Select background texture based on tier
        val backgroundTexture = getBackgroundTexture(tier, spec)

        val panel = ModularPanel.defaultPanel("energy_hatch_${hatchType.name.lowercase()}_${tier.tier}")
            .size(guiWidth, guiHeight)
            .background(backgroundTexture)

        // Title bar
        panel.child(
            IKey.lang(hatch.blockType.translationKey + ".name")
                .asWidget()
                .pos(8, 6)
        )

        // Energy bar
        panel.child(buildEnergyBar(hatch, syncManager))

        // Energy info text
        panel.child(buildEnergyInfo(hatch, syncManager))

        // Player inventory
        panel.child(
            SlotGroupWidget.playerInventory(HIDE_PLAYER_INV_BG)
                .pos(8, guiHeight - 82 - 4)
        )

        // Control buttons
        panel.child(buildControlButtons(hatch, syncManager, hatchType, guiWidth))

        return panel
    }

    private fun buildEnergyBar(
        hatch: EnergyHatchBlockEntity,
        syncManager: PanelSyncManager
    ): ProgressWidget {
        // Register energy sync handler
        val energySyncHandler = EnergySyncHandler(hatch.storage)
        syncManager.syncValue("energy", energySyncHandler)

        return ProgressWidget()
            .progress {
                val energy = hatch.storage.energy.toDouble()
                val capacity = hatch.storage.capacity.toDouble()
                if (capacity <= 0) 0.0 else energy / capacity
            }
            .direction(ProgressWidget.Direction.UP)
            .texture(uiTextures.defaultProgressEmpty, uiTextures.defaultProgressFull, ENERGY_BAR_HEIGHT)
            .size(ENERGY_BAR_WIDTH, ENERGY_BAR_HEIGHT)
            .pos(ENERGY_BAR_X, ENERGY_BAR_Y)
            .tooltip { tooltip ->
                val energy = hatch.storage.energy
                val capacity = hatch.storage.capacity
                val energyStr = NumberFormatter.formatEnergy(energy)
                val capacityStr = NumberFormatter.formatEnergy(capacity)
                tooltip.addLine(IKey.str("$energyStr / $capacityStr FE"))

                val maxReceive = hatch.storage.maxReceive
                val maxExtract = hatch.storage.maxExtract
                if (maxReceive > 0) {
                    tooltip.addLine(
                        IKey.lang(
                            "prototypemachinery.gui.hatch.energy.input_rate",
                            NumberFormatter.formatEnergy(maxReceive)
                        )
                    )
                }
                if (maxExtract > 0) {
                    tooltip.addLine(
                        IKey.lang(
                            "prototypemachinery.gui.hatch.energy.output_rate",
                            NumberFormatter.formatEnergy(maxExtract)
                        )
                    )
                }
            }
    }

    private fun buildEnergyInfo(
        hatch: EnergyHatchBlockEntity,
        syncManager: PanelSyncManager
    ): com.cleanroommc.modularui.api.widget.IWidget {
        return IKey.dynamic {
            val energy = hatch.storage.energy
            val capacity = hatch.storage.capacity
            "${NumberFormatter.formatCompact(energy)} / ${NumberFormatter.formatCompact(capacity)} FE"
        }.asWidget()
            .pos(ENERGY_BAR_X - 30, ENERGY_BAR_Y + ENERGY_BAR_HEIGHT + 4)
    }

    private fun buildControlButtons(
        hatch: EnergyHatchBlockEntity,
        syncManager: PanelSyncManager,
        hatchType: HatchType,
        guiWidth: Int
    ): com.cleanroommc.modularui.api.widget.IWidget {
        val buttonRow = Row()
        buttonRow.pos(guiWidth - 40, 4)
        buttonRow.height(16)

        // Auto-input button (only for INPUT and IO)
        if (hatchType != HatchType.OUTPUT) {
            val autoInputSync = BooleanSyncValue(
                BooleanSupplier { hatch.autoInput },
                BooleanConsumer { hatch.autoInput = it }
            )

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

            buttonRow.child(
                ToggleButton()
                    .value(autoOutputSync)
                    .size(16, 16)
                    .tooltip { it.addLine(IKey.lang("prototypemachinery.gui.hatch.auto_output")) }
            )
        }

        return buttonRow
    }

    private fun getBackgroundTexture(tier: HatchTier, spec: GuiTextureSpec): IDrawable {
        val tierLevel = tier.tier
        val fileName = "gui_energy_hatch_${tierLevel}.png"

        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/gui_energy_hatch/$fileName"))
            .imageSize(spec.texSize, spec.texSize)
            .subAreaXYWH(0, 0, spec.uiWidth, spec.uiHeight)
            .build()
    }

}
