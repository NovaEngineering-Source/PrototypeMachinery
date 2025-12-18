package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.api.widget.Interactable.Result
import com.cleanroommc.modularui.drawable.Stencil
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import github.kasuminova.prototypemachinery.api.ui.definition.FactoryRecipeProgressListDefinition
import github.kasuminova.prototypemachinery.client.gui.sync.FactoryRecipeProgressSyncHandler
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.resources.I18n
import net.minecraft.util.ResourceLocation
import kotlin.math.roundToInt
import java.awt.Rectangle as AwtRectangle

/**
 * Reusable recipe progress list widget for ZenScript-defined UIs.
 *
 * - Uses GUI-scoped sync via [FactoryRecipeProgressSyncHandler]
 * - Implements scrolling (wheel + drag handle)
 * - Uses stencil clipping to prevent overflow
 */
public class FactoryRecipeProgressListWidget(
    private val sync: FactoryRecipeProgressSyncHandler,
    private val def: FactoryRecipeProgressListDefinition
) : com.cleanroommc.modularui.widget.Widget<FactoryRecipeProgressListWidget>(), Interactable {

    private var scrollIndex: Int = 0
    private var dragging: Boolean = false
    private var dragOffsetY: Int = 0

    private val entryHeight: Int get() = def.entryHeight.coerceAtLeast(1)

    private val visibleRows: Int
        get() {
            val v = def.visibleRows
            if (v > 0) return v
            return (area.height / entryHeight).coerceAtLeast(1)
        }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val maxSlots = sync.getMaxSlots().coerceAtLeast(0)
        val maxScroll = (maxSlots - visibleRows).coerceAtLeast(0)
        if (scrollIndex > maxScroll) scrollIndex = maxScroll

        Stencil.applyAtZero(AwtRectangle(0, 0, area.width, area.height), context)
        try {
            val entriesBySlot = sync.getEntries().associateBy { it.slotIndex }

            for (row in 0 until visibleRows) {
                val slotIndex = scrollIndex + row
                if (slotIndex >= maxSlots) break

                val y = row * entryHeight
                drawRecipeEntry(context, widgetTheme, slotIndex, y, entriesBySlot[slotIndex])
            }

            drawScrollHandle(context, widgetTheme, maxScroll)
        } finally {
            Stencil.remove()
        }

        super.draw(context, widgetTheme)
    }

    private fun drawRecipeEntry(
        context: ModularGuiContext,
        widgetTheme: WidgetThemeEntry<*>,
        slotIndex: Int,
        y: Int,
        entry: FactoryRecipeProgressSyncHandler.Entry?
    ) {
        // Background
        RECIPE_ENTRY_BG.draw(context, 0, y, area.width, entryHeight, widgetTheme.theme)

        val percent = (entry?.percent ?: 0).coerceIn(0, 100)
        val isError = entry?.isError ?: false
        val rawMsg = entry?.message.orEmpty()

        // Foreground (progress)
        val fgW = ((percent / 100.0) * area.width).toInt().coerceIn(0, area.width)
        if (fgW > 0) {
            getFgByWidth(fgW).draw(context, 0, y, fgW, entryHeight, widgetTheme.theme)
        }

        val mc = Minecraft.getMinecraft()
        val fr = mc.fontRenderer

        GlStateManager.pushMatrix()
        GlStateManager.translate(0f, 0f, 200f)

        val slotText = if (def.showSlotIndex) "#${slotIndex + 1}" else ""
        val percentText = if (!def.showPercent || entry == null) "" else "$percent%"

        val statusText = when {
            entry == null -> I18n.format(def.idleKey)
            isError -> {
                if (rawMsg.isBlank()) {
                    I18n.format(def.errorKey)
                } else {
                    I18n.format(def.errorWithMessageKey, rawMsg)
                }
            }
            rawMsg.isBlank() -> I18n.format(def.runningKey)
            else -> rawMsg
        }

        val titleColor = 0xFFFFFF
        val percentColor = if (entry == null) 0xAAAAAA else if (isError) 0xFF5555 else 0x55FF55
        val statusColor = if (entry == null) 0xAAAAAA else if (isError) 0xFF5555 else 0xFFFFFF

        // Row 1
        if (slotText.isNotBlank()) {
            fr.drawString(slotText, 4f, (y + 4).toFloat(), titleColor, false)
        }
        if (percentText.isNotBlank()) {
            val pw = fr.getStringWidth(percentText)
            fr.drawString(percentText, (area.width - 4 - pw).toFloat(), (y + 4).toFloat(), percentColor, false)
        }

        // Row 2
        val maxW = (area.width - 8).coerceAtLeast(1)
        val trimmed = fr.trimStringToWidth(statusText, maxW)
        fr.drawString(trimmed, 4f, (y + 16).toFloat(), statusColor, false)

        GlStateManager.popMatrix()
    }

    private fun drawScrollHandle(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>, maxScroll: Int) {
        if (maxScroll <= 0) return

        val trackX = area.width - def.scrollOffsetX - RECIPE_SCROLL_HANDLE_W
        val trackY = def.scrollOffsetY
        val trackH = (area.height - def.scrollOffsetY * 2).coerceAtLeast(1)
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
        val maxScroll = (maxSlots - visibleRows).coerceAtLeast(0)
        if (maxScroll <= 0) return false

        val delta = if (direction == UpOrDown.UP) -amount else amount
        val next = (scrollIndex + delta).coerceIn(0, maxScroll)
        scrollIndex = next

        // Consume wheel inside the list area to avoid scrolling the underlying screen.
        return true
    }

    override fun onMousePressed(mouseButton: Int): Result {
        if (mouseButton != 0) return Result.IGNORE
        if (!isHovering) return Result.IGNORE

        val maxSlots = sync.getMaxSlots().coerceAtLeast(0)
        val maxScroll = (maxSlots - visibleRows).coerceAtLeast(0)
        if (maxScroll <= 0) return Result.IGNORE

        val trackX = area.width - def.scrollOffsetX - RECIPE_SCROLL_HANDLE_W
        val trackY = def.scrollOffsetY
        val trackH = (area.height - def.scrollOffsetY * 2).coerceAtLeast(1)
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
        val maxScroll = (maxSlots - visibleRows).coerceAtLeast(0)
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

        val trackY = def.scrollOffsetY
        val trackH = (area.height - def.scrollOffsetY * 2).coerceAtLeast(1)
        val usableH = (trackH - RECIPE_SCROLL_HANDLE_H).coerceAtLeast(1)

        val clamped = (localY - trackY - dragOffsetY).coerceIn(0, usableH)
        val frac = clamped.toDouble() / usableH.toDouble()
        val next = (frac * maxScroll.toDouble()).roundToInt().coerceIn(0, maxScroll)
        scrollIndex = next
    }

    private fun getFgByWidth(w: Int): IDrawable {
        val key = w.coerceIn(0, 89)
        return RECIPE_ENTRY_FG_BY_W[key]
    }

    private companion object {
        private val STATES_LOC = ResourceLocation("prototypemachinery", "textures/gui/states.png")
        private val CONTROLLER_STATES_LOC = ResourceLocation("prototypemachinery", "textures/gui/gui_controller_states.png")
        private const val TEX_SIZE = 256

        // Entry slice in gui_controller_states.png
        private const val ENTRY_SLICE_W = 89
        private const val ENTRY_SLICE_H = 33

        private val RECIPE_ENTRY_BG: IDrawable = UITexture.builder()
            .location(CONTROLLER_STATES_LOC)
            .imageSize(256, 256)
            .subAreaXYWH(0, 0, ENTRY_SLICE_W, ENTRY_SLICE_H)
            .build()

        // Foreground (progress): X0 Y34. Cached by width for cheap clipping.
        private val RECIPE_ENTRY_FG_BY_W: Array<IDrawable> = Array(ENTRY_SLICE_W + 1) { w ->
            if (w <= 0) IDrawable.EMPTY
            else UITexture.builder()
                .location(CONTROLLER_STATES_LOC)
                .imageSize(256, 256)
                .subAreaXYWH(0, 34, w, ENTRY_SLICE_H)
                .build()
        }

        // Scroll handle (states.png) - normal/hover/pressed at (146,1)/(159,1)/(172,1), tex slice is 13x15, draw is 38x15.
        private const val RECIPE_SCROLL_HANDLE_W = 38
        private const val RECIPE_SCROLL_HANDLE_H = 15
        private const val SCROLL_TEX_W = 13
        private const val SCROLL_TEX_H = 15

        private val RECIPE_SCROLL_HANDLE_NORMAL: IDrawable = UITexture.builder()
            .location(STATES_LOC)
            .imageSize(TEX_SIZE, TEX_SIZE)
            .subAreaXYWH(146, 1, SCROLL_TEX_W, SCROLL_TEX_H)
            .build()

        private val RECIPE_SCROLL_HANDLE_HOVER: IDrawable = UITexture.builder()
            .location(STATES_LOC)
            .imageSize(TEX_SIZE, TEX_SIZE)
            .subAreaXYWH(159, 1, SCROLL_TEX_W, SCROLL_TEX_H)
            .build()

        private val RECIPE_SCROLL_HANDLE_PRESSED: IDrawable = UITexture.builder()
            .location(STATES_LOC)
            .imageSize(TEX_SIZE, TEX_SIZE)
            .subAreaXYWH(172, 1, SCROLL_TEX_W, SCROLL_TEX_H)
            .build()
    }
}
