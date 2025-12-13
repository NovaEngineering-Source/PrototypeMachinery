package github.kasuminova.prototypemachinery.common.block.hatch.fluid

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
import github.kasuminova.prototypemachinery.client.gui.sync.ResourceStorageSyncHandler
import github.kasuminova.prototypemachinery.common.block.hatch.HatchTier
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
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

    // GUI dimensions
    private const val GUI_WIDTH = 192
    private const val GUI_HEIGHT = 182

    // Tank grid configuration
    private const val SLOT_SIZE = 18
    private const val TANKS_PER_ROW = 3
    private const val MAX_VISIBLE_ROWS = 3

    // Section positions
    private const val INPUT_SECTION_X = 8
    private const val OUTPUT_SECTION_X = 100

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

        // Select background texture based on tier
        val backgroundTexture = getBackgroundTexture(tier)

        val panel = ModularPanel.defaultPanel("fluid_io_hatch_${tier.tier}")
            .size(GUI_WIDTH, GUI_HEIGHT)
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
                .pos(16, GUI_HEIGHT - 82 - 4)
        )

        // Control buttons
        panel.child(buildControlButtons(hatch, syncManager))

        return panel
    }

    private fun buildInputSection(
        hatch: FluidIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: FluidIOHatchConfig
    ): IWidget {
        val storageSync: ResourceStorageSyncHandler<PMKey<FluidStack>> = ResourceStorageSyncHandler(
            hatch.inputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMFluidKeyType.readNBT(nbt) as? PMKey<FluidStack> }
        )
        syncManager.syncValue("inputStorage", storageSync)

        val container = Column()
            .pos(INPUT_SECTION_X, 30)
            .width(SLOT_SIZE * TANKS_PER_ROW)
            .height(SLOT_SIZE * MAX_VISIBLE_ROWS)

        container.child(
            IKey.dynamic {
                val total = hatch.inputStorage.getTotalAmount()
                val cap = config.inputTankCapacity * config.inputTankCount.toLong()
                "${total} / ${cap} mB"
            }.asWidget()
        )

        return container
    }

    private fun buildOutputSection(
        hatch: FluidIOHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: FluidIOHatchConfig
    ): IWidget {
        val storageSync: ResourceStorageSyncHandler<PMKey<FluidStack>> = ResourceStorageSyncHandler(
            hatch.outputStorage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMFluidKeyType.readNBT(nbt) as? PMKey<FluidStack> }
        )
        syncManager.syncValue("outputStorage", storageSync)

        val container = Column()
            .pos(OUTPUT_SECTION_X, 30)
            .width(SLOT_SIZE * TANKS_PER_ROW)
            .height(SLOT_SIZE * MAX_VISIBLE_ROWS)

        container.child(
            IKey.dynamic {
                val total = hatch.outputStorage.getTotalAmount()
                val cap = config.outputTankCapacity * config.outputTankCount.toLong()
                "${total} / ${cap} mB"
            }.asWidget()
        )

        return container
    }

    private fun buildControlButtons(
        hatch: FluidIOHatchBlockEntity,
        syncManager: PanelSyncManager
    ): com.cleanroommc.modularui.api.widget.IWidget {
        val buttonRow = Row()
        buttonRow.pos(GUI_WIDTH - 40, 4)
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
        val fileName = "gui_fluid_hatch_${tierLevel}.png"

        return UITexture.fullImage(
            PrototypeMachinery.MOD_ID,
            "textures/gui/gui_fluid_hatch/$fileName"
        )
    }

}
