package github.kasuminova.prototypemachinery.client.gui

import com.cleanroommc.modularui.api.ITheme
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.Rectangle
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.utils.Color
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.PageButton
import com.cleanroommc.modularui.widgets.PagedWidget
import com.cleanroommc.modularui.widgets.SlotGroupWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.layout.Grid
import com.cleanroommc.modularui.widgets.slot.ItemSlot
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.ItemStackHandler

/**
 * Default native UI provider for machines.
 * 机器的默认原生 UI 提供者。
 */
public object DefaultMachineUI {

    // Background texture for the main panel (Tab A - Main)
    private val PANEL_BACKGROUND_A: IDrawable = UITexture.builder()
        .location(ResourceLocation("prototypemachinery", "textures/gui/gui_controller_a.png"))
        .imageSize(384, 256)
        .adaptable(4)
        .build()

    // Background texture for the secondary panel (Tab B - Extension)
    private val PANEL_BACKGROUND_B: IDrawable = UITexture.builder()
        .location(ResourceLocation("prototypemachinery", "textures/gui/gui_controller_b.png"))
        .imageSize(384, 256)
        .adaptable(4)
        .build()

    // Content area background
    private val CONTENT_BACKGROUND: IDrawable = Rectangle()
        .color(Color.withAlpha(Color.GREY.darker(2), 0.3f))

    // ==========================================
    // Texture Configuration
    // ==========================================
    private val STATES_LOC = ResourceLocation("prototypemachinery", "textures/gui/states.png")
    private const val TEX_SIZE = 256
    private const val TAB_W = 21
    private const val TAB_H = 22

    // Coordinates for Tab 1 (Main) - Order: Inactive, Hover, Active
    private const val TAB_1_X = 185
    private const val TAB_1_Y = 182

    // Coordinates for Tab 2 (Extension) - Order: Inactive, Hover, Active
    private const val TAB_2_X = 185
    private const val TAB_2_Y = 207

    private const val TAB_TEX_OFFSET = 3

    private fun getTabTexture(x: Int, y: Int, offsetIndex: Int): IDrawable {
        return UITexture.builder()
            .location(STATES_LOC)
            .imageSize(TEX_SIZE, TEX_SIZE)
            .subAreaXYWH(x + (offsetIndex * (TAB_W + TAB_TEX_OFFSET)), y, TAB_W, TAB_H)
            .build()
    }

    private val TAB_1_INACTIVE get() = getTabTexture(TAB_1_X, TAB_1_Y, 0)
    private val TAB_1_HOVER get() = getTabTexture(TAB_1_X, TAB_1_Y, 1)
    private val TAB_1_ACTIVE get() = getTabTexture(TAB_1_X, TAB_1_Y, 2)

    private val TAB_2_INACTIVE get() = getTabTexture(TAB_2_X, TAB_2_Y, 0)
    private val TAB_2_HOVER get() = getTabTexture(TAB_2_X, TAB_2_Y, 1)
    private val TAB_2_ACTIVE get() = getTabTexture(TAB_2_X, TAB_2_Y, 2)

    // SlotConsumer to hide slot backgrounds
    private val HIDE_SLOT_BACKGROUND: SlotGroupWidget.SlotConsumer = SlotGroupWidget.SlotConsumer { _, slot ->
        slot.background(IDrawable.EMPTY)
    }

    public fun build(machine: MachineBlockEntity, syncManager: PanelSyncManager): ModularPanel {
        val panel = ModularPanel.defaultPanel("default_machine_ui")
            .size(384, 256)
            .background(PANEL_BACKGROUND_A)

        // Create page controller
        val pageController = PagedWidget.Controller()

        // ==========================================
        // Tab Bar (Left sidebar) - absolute positioned
        // ==========================================
        val tabBar = Column()
            .pos(0, 0)
            .size(22, 80)

        // Tab 1: Main Page (with inventory)
        val tab1 = object : PageButton(0, pageController) {
            override fun getCurrentBackground(theme: ITheme, widgetTheme: WidgetThemeEntry<*>): IDrawable {
                if (isActive) return TAB_1_ACTIVE
                if (isHovering) return TAB_1_HOVER
                return TAB_1_INACTIVE
            }
        }
            .size(TAB_W, TAB_H)
//            .marginBottom(2)

        // Tab 2: Extension Page (empty for modpack authors)
        val tab2 = object : PageButton(1, pageController) {
            override fun getCurrentBackground(theme: ITheme, widgetTheme: WidgetThemeEntry<*>): IDrawable {
                if (isActive) return TAB_2_ACTIVE
                if (isHovering) return TAB_2_HOVER
                return TAB_2_INACTIVE
            }
        }
            .size(TAB_W, TAB_H)

        tabBar.child(tab1)
        tabBar.child(tab2)

        // ==========================================
        // Paged Content Area
        // ==========================================
        @Suppress("UNCHECKED_CAST")
        val pagedWidget = PagedWidget()
            .pos(0, 0)
            .size(384, 256)
            .controller(pageController) as PagedWidget<*>

        pagedWidget.addPage(buildMainPage(machine))
        pagedWidget.addPage(buildExtensionPage(machine))

        // Update background based on active page
        pagedWidget.onPageChange { page ->
            if (page == 0) {
                panel.background(PANEL_BACKGROUND_A)
            } else {
                panel.background(PANEL_BACKGROUND_B)
            }
        }

        panel.child(pagedWidget)
        panel.child(tabBar)

        return panel
    }

    /**
     * Build the main page with player inventory.
     * 构建包含玩家物品栏的主界面。
     */
    private fun buildMainPage(machine: MachineBlockEntity): IWidget {
        val page = Column()
            .size(384, 256)

        // Main Content Area
        val mainContent = Column()
            .pos(140, 9)
            .size(236, 156)
            .background(CONTENT_BACKGROUND)
            .padding(4)

        // Machine Name
        mainContent.child(
            TextWidget(machine.machine.type.name)
                .color(Color.WHITE.main)
                .shadow(true)
        )

        // Status Text
        mainContent.child(
            TextWidget("Status: Idle")
                .top(14)
                .color(Color.GREY.brighter(1))
        )

        // Left: 4x4 Grid (Placeholder for machine slots/upgrades)
        val gridArea = Grid()
            .pos(138, 173)
            .size(72, 72)
            .matrix(buildGridSlots(4, 4) as List<List<IWidget>>)

        // Right: Player Inventory
        val playerInvWidget = SlotGroupWidget.playerInventory(HIDE_SLOT_BACKGROUND)
            .pos(215, 171)

        page.child(mainContent)
        page.child(gridArea)
        page.child(playerInvWidget)

        return page
    }

    /**
     * Build the extension page (empty for modpack authors to extend).
     * 构建扩展页面（留空供整合包作者扩展）。
     */
    private fun buildExtensionPage(machine: MachineBlockEntity): IWidget {
        val page = Column()
            .size(384, 256)

        // Content Area with placeholder text
        val content = Column()
            .pos(140, 9)
            .size(236, 239)
            .background(CONTENT_BACKGROUND)
            .padding(4)

        // Machine Name
        content.child(
            TextWidget(machine.machine.type.name)
                .color(Color.WHITE.main)
                .shadow(true)
        )

        // Placeholder Text
        content.child(
            TextWidget("Extension Page")
                .top(20)
                .color(Color.GREY.brighter(1))
        )

        content.child(
            TextWidget("This page is reserved for modpack customization.")
                .top(34)
                .color(Color.GREY.main)
        )

        page.child(content)

        return page
    }

    private fun buildGridSlots(cols: Int, rows: Int): List<List<ItemSlot>> {
        val matrix = mutableListOf<List<ItemSlot>>()
        // Dummy inventory for placeholders
        val dummyInv = ItemStackHandler(cols * rows)
        var index = 0
        for (r in 0 until rows) {
            val row = mutableListOf<ItemSlot>()
            for (c in 0 until cols) {
                val slot = ItemSlot()
                    .slot(dummyInv, index)
                    .background(IDrawable.EMPTY)
                row.add(slot)
                index++
            }
            matrix.add(row)
        }
        return matrix
    }
}
