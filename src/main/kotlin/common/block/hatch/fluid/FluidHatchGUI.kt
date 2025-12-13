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
import github.kasuminova.prototypemachinery.common.block.hatch.HatchType
import github.kasuminova.prototypemachinery.impl.key.fluid.PMFluidKeyType
import net.minecraftforge.fluids.FluidStack
import net.minecraft.util.ResourceLocation
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

    private data class GuiTextureSpec(
        val texSize: Int,
        val uiWidth: Int,
        val uiHeight: Int
    )

    // Derived from Python analysis of textures (gui_fluid_hatch_*).
    private fun getTextureSpec(tier: HatchTier): GuiTextureSpec {
        return if (tier.tier <= 8) {
            GuiTextureSpec(256, 225, 168)
        } else {
            GuiTextureSpec(256, 243, 168)
        }
    }

    private val HIDE_PLAYER_INV_BG: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

    // Slot grid configuration
    private const val SLOT_SIZE = 18
    private const val SLOTS_PER_ROW = 5
    private const val MAX_VISIBLE_ROWS = 5

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

        val spec = getTextureSpec(tier)
        val guiWidth = spec.uiWidth
        val guiHeight = spec.uiHeight

        // Select background texture based on tier
        val backgroundTexture = getBackgroundTexture(tier, hatchType, spec)

        val panel = ModularPanel.defaultPanel("fluid_hatch_${hatchType.name.lowercase()}_${tier.tier}")
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
            SlotGroupWidget.playerInventory(HIDE_PLAYER_INV_BG)
                .pos(8, guiHeight - 82 - 4)
        )

        // Auto-input/output buttons
        panel.child(buildControlButtons(hatch, syncManager, hatchType, guiWidth))

        return panel
    }

    private fun buildMainContent(
        hatch: FluidHatchBlockEntity,
        syncManager: PanelSyncManager,
        config: FluidHatchConfig
    ): IWidget {
        // Sync the whole storage so client can see amounts
        val storageSync: ResourceStorageSyncHandler<PMKey<FluidStack>> = ResourceStorageSyncHandler(
            hatch.storage,
            keyWriter = { key, nbt -> key.writeNBT(nbt) },
            keyReader = { nbt -> PMFluidKeyType.readNBT(nbt) as? PMKey<FluidStack> }
        )
        syncManager.syncValue("storage", storageSync)

        val column = Column()
            .pos(8, 18)
            .widthRel(1f)

        column.child(
            IKey.dynamic {
                val total = hatch.storage.getTotalAmount()
                val totalCapacity = config.tankCapacity * config.tankCount.toLong()
                "${total} / ${totalCapacity} mB"
            }.asWidget()
        )
        column.child(
            IKey.dynamic {
                val used = hatch.storage.usedTypes
                "Types: ${used} / ${config.tankCount}"
            }.asWidget().top(12)
        )

        return column
    }

    private fun buildControlButtons(
        hatch: FluidHatchBlockEntity,
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

    private fun getBackgroundTexture(tier: HatchTier, hatchType: HatchType, spec: GuiTextureSpec): IDrawable {
        val tierLevel = tier.tier
        val fileName = "gui_fluid_hatch_${tierLevel}.png"

        return UITexture.builder()
            .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/gui_fluid_hatch/$fileName"))
            .imageSize(spec.texSize, spec.texSize)
            .subAreaXYWH(0, 0, spec.uiWidth, spec.uiHeight)
            .build()
    }

}
