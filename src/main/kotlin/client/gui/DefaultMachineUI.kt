package github.kasuminova.prototypemachinery.client.gui

import com.cleanroommc.modularui.api.ITheme
import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.api.widget.Interactable.Result
import com.cleanroommc.modularui.drawable.Rectangle
import com.cleanroommc.modularui.drawable.Stencil
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
import com.cleanroommc.modularui.widgets.slot.ItemSlot
import github.kasuminova.prototypemachinery.api.machine.component.getFirstComponentOfType
import github.kasuminova.prototypemachinery.api.machine.component.type.FactoryRecipeProcessorComponent
import github.kasuminova.prototypemachinery.client.gui.sync.FactoryRecipeProgressSyncHandler
import github.kasuminova.prototypemachinery.client.gui.widget.PMSmoothGrid
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.resources.I18n
import net.minecraft.util.ResourceLocation
import net.minecraftforge.items.ItemStackHandler
import kotlin.math.roundToInt
import java.awt.Rectangle as AwtRectangle

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
    private val CONTROLLER_STATES_LOC = ResourceLocation("prototypemachinery", "textures/gui/gui_controller_states.png")
    private const val TEX_SIZE = 256
    private const val TAB_W = 21
    private const val TAB_H = 22

    // ==========================================
    // Recipe progress list (FactoryRecipeProcessor)
    // ==========================================
    private const val RECIPE_LIST_X = 27
    private const val RECIPE_LIST_Y = 8
    private const val RECIPE_LIST_W = 89
    private const val RECIPE_LIST_H = 238

    private const val RECIPE_ENTRY_W = 89
    private const val RECIPE_ENTRY_H = 33
    private const val RECIPE_LIST_VISIBLE = 7

    // Scroll handle (states.png) - user requested: normal/hover/pressed at (146,1)/(159,1)/(172,1), W38 H15.
    private const val RECIPE_SCROLL_OFFSET_X = 4
    private const val RECIPE_SCROLL_OFFSET_Y = 0
    private const val RECIPE_SCROLL_HANDLE_W = 38
    private const val RECIPE_SCROLL_HANDLE_H = 15
    private const val RECIPE_SCROLL_TEX_W = 13
    private const val RECIPE_SCROLL_TEX_H = 15

    private val RECIPE_SCROLL_HANDLE_NORMAL: IDrawable = UITexture.builder()
        .location(STATES_LOC)
        .imageSize(TEX_SIZE, TEX_SIZE)
        .subAreaXYWH(146, 1, RECIPE_SCROLL_TEX_W, RECIPE_SCROLL_TEX_H)
        .build()

    private val RECIPE_SCROLL_HANDLE_HOVER: IDrawable = UITexture.builder()
        .location(STATES_LOC)
        .imageSize(TEX_SIZE, TEX_SIZE)
        .subAreaXYWH(159, 1, RECIPE_SCROLL_TEX_W, RECIPE_SCROLL_TEX_H)
        .build()

    private val RECIPE_SCROLL_HANDLE_PRESSED: IDrawable = UITexture.builder()
        .location(STATES_LOC)
        .imageSize(TEX_SIZE, TEX_SIZE)
        .subAreaXYWH(172, 1, RECIPE_SCROLL_TEX_W, RECIPE_SCROLL_TEX_H)
        .build()

    // Background: X0 Y0 W89 H33
    private val RECIPE_ENTRY_BG: IDrawable = UITexture.builder()
        .location(CONTROLLER_STATES_LOC)
        .imageSize(256, 256)
        .subAreaXYWH(0, 0, RECIPE_ENTRY_W, RECIPE_ENTRY_H)
        .build()

    // Foreground (progress): X0 Y34, same size. Cached by width for cheap clipping.
    private val RECIPE_ENTRY_FG_BY_W: Array<IDrawable> = Array(RECIPE_ENTRY_W + 1) { w ->
        if (w <= 0) IDrawable.EMPTY
        else UITexture.builder()
            .location(CONTROLLER_STATES_LOC)
            .imageSize(256, 256)
            .subAreaXYWH(0, 34, w, RECIPE_ENTRY_H)
            .build()
    }

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
        val pagedWidget = PagedWidget()
            .pos(0, 0)
            .size(384, 256)
            .controller(pageController)

        pagedWidget.addPage(buildMainPage(machine, syncManager))
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
    private fun buildMainPage(machine: MachineBlockEntity, syncManager: PanelSyncManager): IWidget {
        val page = Column()
            .size(384, 256)

        // ==========================================
        // Recipe progress list (FactoryRecipeProcessorComponent only)
        // ==========================================
        val hasFactoryProcessor = machine.machine.componentMap.getFirstComponentOfType<FactoryRecipeProcessorComponent>() != null
        if (hasFactoryProcessor) {
            val recipeSync = FactoryRecipeProgressSyncHandler(machine)
            syncManager.syncValue("factoryRecipeProgress", recipeSync)

            page.child(
                FactoryRecipeProgressListWidget(recipeSync)
                    .pos(RECIPE_LIST_X, RECIPE_LIST_Y)
                    .size(RECIPE_LIST_W, RECIPE_LIST_H)
            )
        }

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
        val gridArea = PMSmoothGrid()
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

    private class FactoryRecipeProgressListWidget(
        private val sync: FactoryRecipeProgressSyncHandler
    ) : com.cleanroommc.modularui.widget.Widget<FactoryRecipeProgressListWidget>(), Interactable {

        private var scrollIndex: Int = 0
        private var dragging: Boolean = false
        private var dragOffsetY: Int = 0

        override fun draw(context: com.cleanroommc.modularui.screen.viewport.ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
            val maxSlots = sync.getMaxSlots().coerceAtLeast(0)
            val maxScroll = (maxSlots - RECIPE_LIST_VISIBLE).coerceAtLeast(0)
            if (scrollIndex > maxScroll) scrollIndex = maxScroll

            // Clip list area (including scrollbar) to avoid bleeding into surrounding UI.
            Stencil.applyAtZero(AwtRectangle(0, 0, area.width, area.height), context)
            try {
                val entriesBySlot = sync.getEntries().associateBy { it.slotIndex }

                for (row in 0 until RECIPE_LIST_VISIBLE) {
                    val slotIndex = scrollIndex + row
                    if (slotIndex >= maxSlots) break

                    val y = row * RECIPE_ENTRY_H
                    drawRecipeEntry(context, widgetTheme, slotIndex, y, entriesBySlot[slotIndex])
                }

                drawScrollHandle(context, widgetTheme, maxScroll)
            } finally {
                Stencil.remove()
            }

            super.draw(context, widgetTheme)
        }

        private fun drawRecipeEntry(
            context: com.cleanroommc.modularui.screen.viewport.ModularGuiContext,
            widgetTheme: WidgetThemeEntry<*>,
            slotIndex: Int,
            y: Int,
            entry: FactoryRecipeProgressSyncHandler.Entry?
        ) {
            // Background
            RECIPE_ENTRY_BG.draw(context, 0, y, RECIPE_ENTRY_W, RECIPE_ENTRY_H, widgetTheme.theme)

            val percent = (entry?.percent ?: 0).coerceIn(0, 100)
            val isError = entry?.isError ?: false
            val rawMsg = entry?.message.orEmpty()

            // Foreground (progress) - clip by selecting a pre-sliced drawable.
            val fgW = ((percent / 100.0) * RECIPE_ENTRY_W).toInt().coerceIn(0, RECIPE_ENTRY_W)
            if (fgW > 0) {
                RECIPE_ENTRY_FG_BY_W[fgW].draw(context, 0, y, fgW, RECIPE_ENTRY_H, widgetTheme.theme)
            }

            // Text overlay
            val mc = Minecraft.getMinecraft()
            val fr = mc.fontRenderer

            GlStateManager.pushMatrix()
            GlStateManager.translate(0f, 0f, 200f)

            val slotText = "#${slotIndex + 1}"
            val percentText = if (entry == null) "" else "$percent%"

            val statusText = when {
                entry == null -> I18n.format("prototypemachinery.gui.recipe_progress.idle")
                isError -> {
                    if (rawMsg.isBlank()) {
                        I18n.format("prototypemachinery.gui.recipe_progress.error")
                    } else {
                        I18n.format("prototypemachinery.gui.recipe_progress.error_with_message", rawMsg)
                    }
                }
                rawMsg.isBlank() -> I18n.format("prototypemachinery.gui.recipe_progress.running")
                else -> rawMsg
            }

            val titleColor = 0xFFFFFF
            val percentColor = if (entry == null) 0xAAAAAA else if (isError) 0xFF5555 else 0x55FF55
            val statusColor = if (entry == null) 0xAAAAAA else if (isError) 0xFF5555 else 0xFFFFFF

            // Row 1: #X (left) + percent (right)
            fr.drawString(slotText, 4f, (y + 4).toFloat(), titleColor, false)
            if (percentText.isNotBlank()) {
                val pw = fr.getStringWidth(percentText)
                fr.drawString(percentText, (RECIPE_ENTRY_W - 4 - pw).toFloat(), (y + 4).toFloat(), percentColor, false)
            }

            // Row 2: status (trim)
            val maxW = RECIPE_ENTRY_W - 8
            val trimmed = fr.trimStringToWidth(statusText, maxW)
            fr.drawString(trimmed, 4f, (y + 16).toFloat(), statusColor, false)

            GlStateManager.popMatrix()
        }

        private fun drawScrollHandle(
            context: com.cleanroommc.modularui.screen.viewport.ModularGuiContext,
            widgetTheme: WidgetThemeEntry<*>,
            maxScroll: Int
        ) {
            if (maxScroll <= 0) return

            val trackX = area.width - RECIPE_SCROLL_OFFSET_X - RECIPE_SCROLL_HANDLE_W
            val trackY = RECIPE_SCROLL_OFFSET_Y
            val trackH = (area.height - RECIPE_SCROLL_OFFSET_Y * 2).coerceAtLeast(1)
            val usableH = (trackH - RECIPE_SCROLL_HANDLE_H).coerceAtLeast(1)

            val frac = scrollIndex.toDouble() / maxScroll.toDouble()
            val handleY = trackY + (usableH * frac).roundToInt()

            val localX = context.mouseX
            val localY = context.mouseY
            val hoveringHandle =
                localX in trackX until (trackX + RECIPE_SCROLL_HANDLE_W) &&
                    localY in handleY until (handleY + RECIPE_SCROLL_HANDLE_H)

            val handleDrawable = when {
                dragging -> RECIPE_SCROLL_HANDLE_PRESSED
                hoveringHandle -> RECIPE_SCROLL_HANDLE_HOVER
                else -> RECIPE_SCROLL_HANDLE_NORMAL
            }

            handleDrawable.draw(context, trackX, handleY, RECIPE_SCROLL_HANDLE_W, RECIPE_SCROLL_HANDLE_H, getActiveWidgetTheme(widgetTheme, hoveringHandle))
        }

        override fun onMouseScroll(direction: UpOrDown, amount: Int): Boolean {
            if (!isHovering) return false

            val maxSlots = sync.getMaxSlots().coerceAtLeast(0)
            val maxScroll = (maxSlots - RECIPE_LIST_VISIBLE).coerceAtLeast(0)
            if (maxScroll <= 0) return false

            val delta = if (direction == UpOrDown.UP) -amount else amount
            val next = (scrollIndex + delta).coerceIn(0, maxScroll)
            if (next == scrollIndex) {
                // keep consuming the wheel to avoid accidental scrolling of the underlying screen
                return true
            }
            scrollIndex = next
            return true
        }

        override fun onMousePressed(mouseButton: Int): Result {
            if (mouseButton != 0) return Result.IGNORE
            if (!isHovering) return Result.IGNORE

            val maxSlots = sync.getMaxSlots().coerceAtLeast(0)
            val maxScroll = (maxSlots - RECIPE_LIST_VISIBLE).coerceAtLeast(0)
            if (maxScroll <= 0) return Result.IGNORE

            val trackX = area.width - RECIPE_SCROLL_OFFSET_X - RECIPE_SCROLL_HANDLE_W
            val trackY = RECIPE_SCROLL_OFFSET_Y
            val trackH = (area.height - RECIPE_SCROLL_OFFSET_Y * 2).coerceAtLeast(1)
            val usableH = (trackH - RECIPE_SCROLL_HANDLE_H).coerceAtLeast(1)

            val frac = scrollIndex.toDouble() / maxScroll.toDouble()
            val handleY = trackY + (usableH * frac).roundToInt()

            val localX = context.mouseX
            val localY = context.mouseY
            val hoveringHandle =
                localX in trackX until (trackX + RECIPE_SCROLL_HANDLE_W) &&
                    localY in handleY until (handleY + RECIPE_SCROLL_HANDLE_H)

            if (!hoveringHandle) return Result.IGNORE

            dragging = true
            dragOffsetY = (localY - handleY).coerceIn(0, RECIPE_SCROLL_HANDLE_H)
            updateFromMouse(maxScroll)
            Interactable.playButtonClickSound()
            return Result.SUCCESS
        }

        override fun onMouseDrag(mouseButton: Int, timeSinceClick: Long) {
            if (!dragging || mouseButton != 0) return

            val maxSlots = sync.getMaxSlots().coerceAtLeast(0)
            val maxScroll = (maxSlots - RECIPE_LIST_VISIBLE).coerceAtLeast(0)
            if (maxScroll <= 0) return

            updateFromMouse(maxScroll)
        }

        override fun onMouseRelease(mouseButton: Int): Boolean {
            if (mouseButton != 0) return false
            if (!dragging) return false

            dragging = false
            return true
        }

        private fun updateFromMouse(maxScroll: Int) {
            val ctx = context
            val localY = ctx.mouseY

            val trackY = RECIPE_SCROLL_OFFSET_Y
            val trackH = (area.height - RECIPE_SCROLL_OFFSET_Y * 2).coerceAtLeast(1)
            val usableH = (trackH - RECIPE_SCROLL_HANDLE_H).coerceAtLeast(1)

            val clamped = (localY - trackY - dragOffsetY).coerceIn(0, usableH)
            val frac = clamped.toDouble() / usableH.toDouble()
            val next = (frac * maxScroll.toDouble()).roundToInt().coerceIn(0, maxScroll)
            scrollIndex = next
        }
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
