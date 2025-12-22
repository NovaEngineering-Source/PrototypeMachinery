package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.api.layout.ILayoutWidget
import com.cleanroommc.modularui.api.widget.IParentWidget
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.widget.AbstractScrollWidget
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData
import kotlin.math.max

/**
 * A lightweight generic scrollable container.
 *
 * - Applies stencil clipping automatically (handled by [AbstractScrollWidget]).
 * - Translates children by the current scroll offset.
 * - Computes scroll sizes from children bounds in [layoutWidgets].
 */
public class PMScrollContainerWidget(
    scrollX: HorizontalScrollData? = null,
    scrollY: VerticalScrollData? = null
) : AbstractScrollWidget<IWidget, PMScrollContainerWidget>(scrollX, scrollY),
    ILayoutWidget,
    IParentWidget<IWidget, PMScrollContainerWidget> {

    override fun getThis(): PMScrollContainerWidget = this

    override fun addChild(child: IWidget, index: Int): Boolean {
        return super.addChild(child, index)
    }

    override fun layoutWidgets(): Boolean {
        var maxX = 0
        var maxY = 0

        // Ensure all children have their sizes calculated, then compute the content bounds.
        for (child in children) {
            if (shouldIgnoreChildSize(child)) continue

            val r = child.resizer()
            if (!r.isXCalculated || !r.isYCalculated || !r.isWidthCalculated || !r.isHeightCalculated) {
                return false
            }

            val a = child.area
            maxX = max(maxX, a.rx + a.width)
            maxY = max(maxY, a.ry + a.height)

            // We do not move/resize children here, but we still mark them as handled for layout.
            r.updateResized()
        }

        // Update scroll sizes. ScrollData#clamp will handle the <= visible size case.
        getScrollArea().scrollX?.setScrollSize(maxX)
        getScrollArea().scrollY?.setScrollSize(maxY)

        return true
    }
}
