package github.kasuminova.prototypemachinery.client.gui.scroll

import com.cleanroommc.modularui.animation.Animator
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetTheme
import com.cleanroommc.modularui.utils.Interpolation
import com.cleanroommc.modularui.widget.scroll.ScrollArea
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.client.gui.scroll.PMThinVerticalScrollData.Companion.RESERVED_THICKNESS
import net.minecraft.util.ResourceLocation
import java.util.function.DoubleConsumer
import kotlin.math.abs

/**
 * Thin vertical scrollbar variant for Item IO hatch grids.
 *
 * 供物品 IO 仓网格使用的“细版”竖向滚动条。
 *
 * Spec (from states.png):
 * - Sprites X: 161 / 169 / 177, Y: 17
 * - Order: normal / hover / pressed
 * - Sprite size: 7x12
 *
 * 规格（来自 states.png）：
 * - 精灵坐标 X：161 / 169 / 177，Y：17
 * - 顺序：普通 / 悬停 / 按下
 * - 精灵尺寸：7x12
 *
 * NOTE: We keep the same gutter behavior as [PMVerticalScrollData]:
 * - bar is drawn with an inset offset on the cross axis
 * - Grid reserves [RESERVED_THICKNESS] so slots won't be clipped
 *
 * 注意：此处保持与 [PMVerticalScrollData] 一致的“边槽（gutter）”行为：
 * - 滚动条在副轴方向会内缩绘制（inset offset）
 * - 网格侧会预留 [RESERVED_THICKNESS] 的宽度，避免格子/槽位被裁剪
 */
public class PMThinVerticalScrollData : VerticalScrollData(false, BAR_W) {

    public companion object {
        // Keep consistent with the existing scrollbar gutter unless specified otherwise.
        // 与现有滚动条的边槽行为保持一致（除非另行指定）。
        public const val BAR_OFFSET_X: Int = 3
        public const val BAR_OFFSET_Y: Int = PMVerticalScrollData.BAR_OFFSET_Y

        public const val BAR_W: Int = 7
        public const val BAR_H_SRC: Int = 12

        /**
         * Total reserved strip width used by Grid size.
         * Must include BAR_OFFSET_X + BAR_W.
         *
         * 网格在布局计算中预留的总宽度。
         * 必须包含 BAR_OFFSET_X + BAR_W。
         */
        public const val RESERVED_THICKNESS: Int = BAR_OFFSET_X + BAR_W

        private fun icon(x: Int, y: Int): IDrawable {
            return UITexture.builder()
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
                .imageSize(256, 256)
                .subAreaXYWH(x, y, BAR_W, BAR_H_SRC)
                .build()
        }

        private val NORMAL: IDrawable = icon(161, 17)
        private val HOVER: IDrawable = icon(169, 17)
        private val PRESSED: IDrawable = icon(177, 17)

        /** See [PMVerticalScrollData] for rationale.
         *
         * 具体原因/背景说明见 [PMVerticalScrollData]。
         */
        private const val SCROLL_ANIM_MIN_MS: Int = 180
        private const val SCROLL_ANIM_MAX_MS: Int = 360
        private const val SCROLL_ANIM_BASE_MS: Int = 90
        private const val SCROLL_ANIM_MS_PER_PX: Int = 2
    }

    private val scrollAnimator: Animator = Animator()
        .duration(SCROLL_ANIM_BASE_MS)
        .curve(Interpolation.QUINT_OUT)

    private var animatingTo: Int = 0

    override fun isAnimating(): Boolean = scrollAnimator.isAnimating

    override fun getAnimatingTo(): Int = animatingTo

    override fun getAnimationDirection(): Int {
        if (!isAnimating) return 0
        return if (scrollAnimator.max >= scrollAnimator.min) 1 else -1
    }

    override fun animateTo(area: ScrollArea, x: Int) {
        val from = scroll
        val distance = abs(x - from)
        val duration = (SCROLL_ANIM_BASE_MS + distance * SCROLL_ANIM_MS_PER_PX)
            .coerceIn(SCROLL_ANIM_MIN_MS, SCROLL_ANIM_MAX_MS)

        animatingTo = x
        scrollAnimator
            .duration(duration)
            .curve(Interpolation.CUBIC_OUT)
            .bounds(from.toFloat(), x.toFloat())
            .onUpdate(DoubleConsumer { value ->
                if (scrollTo(area, value.toInt())) {
                    scrollAnimator.stop(false)
                }
            })
        scrollAnimator.reset()
        scrollAnimator.animate()
    }

    override fun getScrollBarLength(area: ScrollArea): Int {
        val full = getFullVisibleSize(area)
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

        val raw = (mainAxisPos - BAR_OFFSET_Y - yOffsetTop - clickOffset).toFloat() / travel.toFloat()

        // Match ScrollArea.drag()'s (maxScroll + thickness) mapping while our rendering uses maxScroll.
        val fullVisible = getFullVisibleSize(area, isOtherActive)
        val maxScroll = (getScrollSize() - fullVisible).coerceAtLeast(0)
        val denom = (maxScroll + thickness).coerceAtLeast(1)
        val scale = if (maxScroll <= 0) 0f else maxScroll.toFloat() / denom.toFloat()
        return raw * scale
    }

    override fun onMouseClicked(area: ScrollArea, mainAxisPos: Int, crossAxisPos: Int, button: Int): Boolean {
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
        val isOtherActive = isOtherScrollBarActive(area, true)

        val baseX = if (isOnAxisStart) 0 else area.w() - RESERVED_THICKNESS
        val x = baseX + BAR_OFFSET_X
        val w = BAR_W

        val trackTop = BAR_OFFSET_Y

        val yOffsetTop = getTopReservedByOther(area, isOtherActive)
        val trackH = getTrackHeight(area)

        val thumbLen = getScrollBarLength(area)
        val y = getThumbY(area, isOtherActive, thumbLen)

        val mx = context.mouseX
        val my = context.mouseY
        val hoveringThumb = mx >= x && mx < x + w && my >= y && my < y + thumbLen

        val drawable = when {
            isDragging -> PRESSED
            hoveringThumb -> HOVER
            else -> NORMAL
        }

        val trackDrawH = (trackH - yOffsetTop).coerceAtLeast(0)
        GuiDraw.drawRect(
            x.toFloat(),
            (trackTop + yOffsetTop).toFloat(),
            w.toFloat(),
            trackDrawH.toFloat(),
            area.scrollBarBackgroundColor
        )

        drawable.draw(context, x, y, w, thumbLen, widgetTheme)
    }
}
