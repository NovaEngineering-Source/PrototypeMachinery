package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget

/**
 * Draws a 1px inset border in the widget's foreground layer (draw step).
 *
 * 在控件前景层（draw 阶段）绘制 1px 的内嵌描边。
 *
 * - Color: #F2F2F2 (ARGB: 0xFFF2F2F2)
 * - Border is drawn inside the widget area.
 *
 * - 颜色：#F2F2F2（ARGB：0xFFF2F2F2）
 * - 描边绘制在控件区域内部。
 *
 * Important: this widget is purely decorative and should not block hover/click interactions.
 *
 * 重要：该控件仅用于装饰，不应阻挡 hover/click 等交互事件。
 */
public class InsetBorderWidget : Widget<InsetBorderWidget>() {

    public companion object {
        public const val DEFAULT_COLOR: Int = 0xFFF2F2F2.toInt()
        public const val DEFAULT_THICKNESS: Int = 1
    }

    private var borderColor: Int = DEFAULT_COLOR
    private var borderThickness: Int = DEFAULT_THICKNESS

    public fun color(argb: Int): InsetBorderWidget = apply { this.borderColor = argb }

    public fun thickness(px: Int): InsetBorderWidget = apply { this.borderThickness = px.coerceAtLeast(1) }

    override fun canHover(): Boolean = false

    override fun canHoverThrough(): Boolean = true

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val w = area.w()
        val h = area.h()
        val t = borderThickness
        if (w <= 0 || h <= 0) return
        if (w < t * 2 || h < t * 2) return

        val wf = w.toFloat()
        val hf = h.toFloat()
        val tf = t.toFloat()

        // top / 顶部
        GuiDraw.drawRect(0f, 0f, wf, tf, borderColor)
        // bottom / 底部
        GuiDraw.drawRect(0f, hf - tf, wf, tf, borderColor)
        // left / 左侧
        GuiDraw.drawRect(0f, tf, tf, hf - tf * 2f, borderColor)
        // right / 右侧
        GuiDraw.drawRect(wf - tf, tf, tf, hf - tf * 2f, borderColor)
    }
}
