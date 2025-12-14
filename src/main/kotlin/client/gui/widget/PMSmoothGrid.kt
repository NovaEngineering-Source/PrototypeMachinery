package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.widgets.layout.Grid

/**
 * A local workaround for smoother scrollbar dragging.
 *
 * In MC 1.12, mouse-drag callbacks can effectively be processed at ~20tps,
 * making scrollbars feel like they're updating at ~20fps.
 *
 * ModularUI's ScrollArea.drag(...) is documented as "should be invoked in a drawing or update method".
 * We therefore update dragging during rendering as well.
 */
public class PMSmoothGrid : Grid() {

    override fun preDraw(context: ModularGuiContext, transformed: Boolean) {
        if (!transformed) {
            val scroll = scrollArea
            if (scroll.isDragging) {
                scroll.drag(context)
            }
        }
        super.preDraw(context, transformed)
    }
}
