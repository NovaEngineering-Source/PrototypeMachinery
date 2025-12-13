package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.Rectangle
import com.cleanroommc.modularui.utils.Color
import com.cleanroommc.modularui.widget.Widget
import com.cleanroommc.modularui.widgets.SlotGroupWidget
import com.cleanroommc.modularui.widgets.slot.ItemSlot
import github.kasuminova.prototypemachinery.api.ui.definition.FluidSlotDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ItemSlotDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ItemSlotGroupDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.PlayerInventoryDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext

public class SlotWidgetFactory : WidgetFactory {

    // Player inventory slot consumer: ensure slots are visible even on custom backgrounds.
    private fun playerInvSlotBackground(ctx: UIBuildContext): SlotGroupWidget.SlotConsumer {
        return SlotGroupWidget.SlotConsumer { _: Int, slot: ItemSlot ->
            slot.background(ctx.textures.defaultSlotBackground)
        }
    }

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is ItemSlotDefinition -> buildItemSlotPlaceholder(def, ctx)
            is FluidSlotDefinition -> buildFluidSlotPlaceholder(def, ctx)
            is ItemSlotGroupDefinition -> buildItemSlotGroupPlaceholder(def)
            is PlayerInventoryDefinition -> buildPlayerInventory(def, ctx)
            else -> null
        }
    }

    private fun buildItemSlotPlaceholder(def: ItemSlotDefinition, ctx: UIBuildContext): IWidget {
        // Placeholder - actual slot implementation needs ItemSlotSH binding
        return Widget()
            .pos(def.x, def.y)
            .size(def.width, def.height)
            .background(ctx.textures.defaultSlotBackground)
    }

    private fun buildFluidSlotPlaceholder(def: FluidSlotDefinition, ctx: UIBuildContext): IWidget {
        return Widget()
            .pos(def.x, def.y)
            .size(def.width, def.height)
            .background(ctx.textures.defaultFluidFrame)
    }

    private fun buildItemSlotGroupPlaceholder(def: ItemSlotGroupDefinition): IWidget {
        val totalWidth = def.columns * 18
        val totalHeight = def.rows * 18
        return Widget()
            .pos(def.x, def.y)
            .size(totalWidth, totalHeight)
            .background(Rectangle().color(Color.withAlpha(Color.GREY.main, 0.2f)))
    }

    private fun buildPlayerInventory(def: PlayerInventoryDefinition, ctx: UIBuildContext): IWidget {
        // Real, interactive player inventory slots (3x9 + hotbar).
        // Sync key is handled by ModularUI internally ("player"), as used by DefaultMachineUI.
        return SlotGroupWidget.playerInventory(playerInvSlotBackground(ctx))
            .pos(def.x, def.y)
            .size(def.width, def.height)
    }
}
