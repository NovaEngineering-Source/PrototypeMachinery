package github.kasuminova.prototypemachinery.client.buildinstrument.widget

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Vertical slider widget for material preview scrolling.
 */
@SideOnly(Side.CLIENT)
internal class MaterialPreviewSliderWidget(
    private val onScrollChanged: (Float) -> Unit
) : Widget<MaterialPreviewSliderWidget>(), Interactable {

    companion object {
        private const val SLIDER_W = 7
        private const val SLIDER_H = 12
        private const val TRACK_PADDING = 1

        private fun guiTex(path: String): UITexture {
            return UITexture.fullImage(
                ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_structure_preview/$path")
            )
        }

        private val SLIDER_DEFAULT by lazy { guiTex("slider_default") }
        private val SLIDER_SELECTED by lazy { guiTex("slider_selected") }
        private val SLIDER_PRESS by lazy { guiTex("slider_press") }
    }

    private var scrollPercent: Float = 0f
    private var dragging: Boolean = false
    private var dragStartY: Int = 0
    private var dragStartPercent: Float = 0f

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val trackH = area.h() - TRACK_PADDING * 2
        val sliderY = TRACK_PADDING + (trackH - SLIDER_H) * scrollPercent

        val tex = when {
            dragging -> SLIDER_PRESS
            isHovering -> SLIDER_SELECTED
            else -> SLIDER_DEFAULT
        }

        tex.draw(TRACK_PADDING.toFloat(), sliderY, SLIDER_W.toFloat(), SLIDER_H.toFloat())

        super.draw(context, widgetTheme)
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton != 0) return Interactable.Result.IGNORE

        dragging = true
        dragStartY = context.mouseY
        dragStartPercent = scrollPercent

        val relY = context.mouseY - area.y - TRACK_PADDING
        val trackH = area.h() - TRACK_PADDING * 2 - SLIDER_H
        if (trackH > 0) {
            val clickPercent = (relY - SLIDER_H / 2f) / trackH
            scrollPercent = clickPercent.coerceIn(0f, 1f)
            onScrollChanged(scrollPercent)
        }

        return Interactable.Result.SUCCESS
    }

    override fun onMouseRelease(mouseButton: Int): Boolean {
        if (mouseButton == 0) {
            dragging = false
        }
        return true
    }

    override fun onMouseDrag(mouseButton: Int, timeSinceClick: Long) {
        if (dragging && mouseButton == 0) {
            val dy = context.mouseY - dragStartY
            val trackH = area.h() - TRACK_PADDING * 2 - SLIDER_H
            if (trackH > 0) {
                val deltaPercent = dy.toFloat() / trackH
                scrollPercent = (dragStartPercent + deltaPercent).coerceIn(0f, 1f)
                onScrollChanged(scrollPercent)
            }
        }
    }

    override fun onMouseScroll(direction: UpOrDown, amount: Int): Boolean {
        val delta = if (direction == UpOrDown.UP) -0.1f else 0.1f
        scrollPercent = (scrollPercent + delta).coerceIn(0f, 1f)
        onScrollChanged(scrollPercent)
        return true
    }

    public fun setScrollPercent(percent: Float) {
        scrollPercent = percent.coerceIn(0f, 1f)
    }
}
