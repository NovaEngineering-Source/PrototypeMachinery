package github.kasuminova.prototypemachinery.client.gui.scroll

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetTheme
import com.cleanroommc.modularui.widget.scroll.ScrollArea
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData
import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.util.ResourceLocation

/**
 * Custom vertical scrollbar for PrototypeMachinery hatch UIs.
 *
 * Spec (from states.png):
 * - Offset relative to scroll area: x + 5, y + 1
 * - Sprites: states.png at (146,1), (159,1), (172,1)
 * - Order: normal / hover / pressed
 * - Sprite size: 12x15
 */
public class PMVerticalScrollData : VerticalScrollData(false, BAR_W) {

    public companion object {
        public const val BAR_OFFSET_X: Int = 5
        public const val BAR_OFFSET_Y: Int = 0

        public const val BAR_W: Int = 12
        public const val BAR_H_SRC: Int = 15

        /**
         * Total reserved strip width used by our Grid size so slots won't be clipped by ScrollArea padding.
         * This must include BAR_OFFSET_X + BAR_W.
         */
        public const val RESERVED_THICKNESS: Int = BAR_OFFSET_X + BAR_W // 17

        private fun icon(x: Int, y: Int): IDrawable {
            return UITexture.builder()
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
                .imageSize(256, 256)
                .subAreaXYWH(x, y, BAR_W, BAR_H_SRC)
                .build()
        }

        private val NORMAL: IDrawable = icon(146, 1)
        private val HOVER: IDrawable = icon(159, 1)
        private val PRESSED: IDrawable = icon(172, 1)
    }

    /**
     * Fixed thumb length to avoid stretching the sprite.
     *
     * Note: ScrollData enforces a minimum length of (thickness + 1). Our thickness is BAR_W,
     * so min length is 13, which is fine for a 15px sprite.
     */
    override fun getScrollBarLength(area: ScrollArea): Int {
        val full = getFullVisibleSize(area)
        // Keep within visible bounds while honoring ScrollData's min length rule.
        val maxLen = (full - 1).coerceAtLeast(getMinLength())
        return BAR_H_SRC.coerceAtMost(maxLen)
    }

    private fun getTopReservedByOther(area: ScrollArea, isOtherActive: Boolean): Int {
        val other = getOtherScrollData(area)
        return if (other != null && isOtherActive && other.isOnAxisStart) other.thickness else 0
    }

    private fun getTrackHeight(area: ScrollArea): Int {
        return (area.h() - BAR_OFFSET_Y - BAR_OFFSET_Y).coerceAtLeast(0)
    }

    private fun getThumbY(area: ScrollArea, isOtherActive: Boolean, thumbLen: Int): Int {
        val yOffsetTop = getTopReservedByOther(area, isOtherActive)
        val trackH = getTrackHeight(area)
        val travel = (trackH - yOffsetTop - thumbLen).coerceAtLeast(1)

        val fullVisible = getFullVisibleSize(area, isOtherActive)
        val maxScroll = (getScrollSize() - fullVisible).coerceAtLeast(1)
        val progress = (getScroll().toFloat() / maxScroll.toFloat()).coerceIn(0f, 1f)
        return BAR_OFFSET_Y + yOffsetTop + (progress * travel.toFloat()).toInt()
    }

    override fun getProgress(area: ScrollArea, mainAxisPos: Int, crossAxisPos: Int): Float {
        val isOtherActive = isOtherScrollBarActive(area, true)
        val thumbLen = getScrollBarLength(area)
        val yOffsetTop = getTopReservedByOther(area, isOtherActive)
        val trackH = getTrackHeight(area)
        val travel = (trackH - yOffsetTop - thumbLen).coerceAtLeast(1)
        return (mainAxisPos - BAR_OFFSET_Y - yOffsetTop - clickOffset).toFloat() / travel.toFloat()
    }

    override fun onMouseClicked(area: ScrollArea, mainAxisPos: Int, crossAxisPos: Int, button: Int): Boolean {
        // Keep the standard cross-axis hitbox: right side strip of width = thickness (BAR_W).
        if (isOnAxisStart) {
            if (crossAxisPos > thickness) return false
        } else {
            if (crossAxisPos < area.w() - thickness) return false
        }

        dragging = true

        val isOtherActive = isOtherScrollBarActive(area, true)
        val thumbLen = getScrollBarLength(area)
        val thumbY = getThumbY(area, isOtherActive, thumbLen)
        val clickInsideThumb = mainAxisPos >= thumbY && mainAxisPos <= thumbY + thumbLen

        clickOffset = if (clickInsideThumb) {
            mainAxisPos - thumbY
        } else {
            thumbLen / 2
        }
        return true
    }

    override fun drawScrollbar(area: ScrollArea, context: ModularGuiContext, widgetTheme: WidgetTheme, texture: IDrawable) {
        // Keep the built-in activation logic (if it isn't active, this method won't be called).
        val isOtherActive = isOtherScrollBarActive(area, true)

        // Reserve strip on the cross-axis so slots are visually separated from the bar.
        // NOTE: thickness (BAR_W) remains small to avoid ScrollData's min-length rule forcing a taller bar.
        val baseX = if (isOnAxisStart) 0 else area.w() - RESERVED_THICKNESS
        val x = baseX + BAR_OFFSET_X
        val w = BAR_W

        val trackTop = BAR_OFFSET_Y

        val yOffsetTop = getTopReservedByOther(area, isOtherActive)
        val trackH = getTrackHeight(area)

        val thumbLen = getScrollBarLength(area)
        val y = getThumbY(area, isOtherActive, thumbLen)

        // Determine state.
        val mx = context.mouseX
        val my = context.mouseY
        val hoveringThumb = mx >= x && mx < x + w && my >= y && my < y + thumbLen

        val drawable = when {
            isDragging -> PRESSED
            hoveringThumb -> HOVER
            else -> NORMAL
        }

        // Optional track background: keep it subtle (same as default) but inside the offset track.
        val trackDrawH = (trackH - yOffsetTop).coerceAtLeast(0)
        GuiDraw.drawRect(
            x.toFloat(),
            (trackTop + yOffsetTop).toFloat(),
            w.toFloat(),
            trackDrawH.toFloat(),
            area.scrollBarBackgroundColor
        )

        // Draw thumb with fixed height to avoid stretching.
        drawable.draw(context, x, y, w, thumbLen, widgetTheme)
    }
}
