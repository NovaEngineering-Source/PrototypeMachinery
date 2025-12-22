package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.widget.ParentWidget
import com.cleanroommc.modularui.widget.scroll.HorizontalScrollData
import com.cleanroommc.modularui.widget.scroll.VerticalScrollData
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.layout.Row
import github.kasuminova.prototypemachinery.api.ui.definition.ColumnDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.GridDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.RowDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ScrollContainerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext
import github.kasuminova.prototypemachinery.client.gui.widget.PMScrollContainerWidget
import github.kasuminova.prototypemachinery.client.gui.widget.PMSmoothGrid

public class LayoutWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is PanelDefinition -> buildNestedPanel(def, ctx, buildChild)
            is RowDefinition -> buildRow(def, buildChild)
            is ColumnDefinition -> buildColumn(def, buildChild)
            is GridDefinition -> buildGrid(def, buildChild)
            is ScrollContainerDefinition -> buildScrollContainer(def, buildChild)
            else -> null
        }
    }

    /**
     * Flow (Row/Column) will ignore children whose position on the main axis is fixed.
     * Our leaf builders default to pos(0,0), which makes Flow treat them as fixed-position,
     * so they won't be laid out and will stack at one point.
     *
     * To keep the existing WidgetDefinition model (x/y always present), we wrap children
     * in a non-layout ParentWidget without fixed pos; Flow lays out the wrapper instead.
     */
    private fun wrapForLayout(def: WidgetDefinition, child: IWidget): IWidget {
        val wrapper = ParentWidget()

        val w = when {
            def.width > 0 -> def.width
            child.area.w() > 0 -> child.area.w()
            else -> 18
        }
        val h = when {
            def.height > 0 -> def.height
            child.area.h() > 0 -> child.area.h()
            else -> 18
        }

        wrapper.size(w, h)
        wrapper.child(child)
        return wrapper
    }

    private fun buildNestedPanel(def: PanelDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget {
        // Panel is meant to be an absolute-position container, not a Flow layout.
        // Using a Column here causes some children (notably nested layout widgets) to be reflowed
        // unexpectedly, leading to overlaps and content drifting when scripts rely on explicit x/y.
        val panel = ParentWidget()
            .pos(def.x, def.y)
            .size(def.width, def.height)

        val bgPath = ctx.textures.normalizeTexturePath(def.backgroundTexture)
        if (bgPath != null) {
            panel.background(ctx.textures.parseTexture(bgPath))
        } else {
            // Disable default theme background when no custom background is set.
            // This prevents parent panel's theme background (e.g., MC_BACKGROUND) from
            // bleeding through transparent areas of child panels.
            // 当没有设置自定义背景时，禁用主题默认背景。
            // 这可以防止父面板的主题背景（如 MC_BACKGROUND）透过子面板的透明区域显示。
            panel.background(IDrawable.EMPTY)
        }

        def.children.forEach { childDef ->
            val widget = buildChild(childDef)
            if (widget != null) {
                panel.child(widget)
            }
        }

        return panel
    }

    private fun buildRow(def: RowDefinition, buildChild: (WidgetDefinition) -> IWidget?): IWidget {
        val row = Row()
            .pos(def.x, def.y)

        if (def.spacing > 0) row.childPadding(def.spacing)

        if (def.width > 0) row.width(def.width)
        if (def.height > 0) row.height(def.height)

        def.children.forEach { childDef ->
            val widget = buildChild(childDef)
            if (widget != null) {
                row.child(wrapForLayout(childDef, widget))
            }
        }

        return row
    }

    private fun buildColumn(def: ColumnDefinition, buildChild: (WidgetDefinition) -> IWidget?): IWidget {
        val column = Column()
            .pos(def.x, def.y)

        if (def.spacing > 0) column.childPadding(def.spacing)

        if (def.width > 0) column.width(def.width)
        if (def.height > 0) column.height(def.height)

        def.children.forEach { childDef ->
            val widget = buildChild(childDef)
            if (widget != null) {
                column.child(wrapForLayout(childDef, widget))
            }
        }

        return column
    }

    private fun buildGrid(def: GridDefinition, buildChild: (WidgetDefinition) -> IWidget?): IWidget {
        val grid = PMSmoothGrid()
            .pos(def.x, def.y)
            .minColWidth(18)
            .minRowHeight(18)

        if (def.rowSpacing > 0 || def.columnSpacing > 0) {
            // Grid spacing is implemented as minimum element margin.
            // Note: margins apply on both sides, so use half to approximate "between" spacing.
            val h = ((def.columnSpacing.coerceAtLeast(0)) + 1) / 2
            val v = ((def.rowSpacing.coerceAtLeast(0)) + 1) / 2
            grid.minElementMargin(h, v)
        }

        if (def.width > 0) grid.width(def.width)
        if (def.height > 0) grid.height(def.height)

        // Build children into rows
        val rows = mutableListOf<MutableList<IWidget>>()
        var currentRow = mutableListOf<IWidget>()

        val columns = def.columns.coerceAtLeast(1)
        def.children.forEachIndexed { index, childDef ->
            val widget = buildChild(childDef)
            if (widget != null) {
                currentRow.add(wrapForLayout(childDef, widget))
                if ((index + 1) % columns == 0) {
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                }
            }
        }
        if (currentRow.isNotEmpty()) {
            rows.add(currentRow)
        }

        @Suppress("UNCHECKED_CAST")
        grid.matrix(rows as List<List<IWidget>>)

        return grid
    }

    private fun buildScrollContainer(def: ScrollContainerDefinition, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        // A scroll container must have a positive viewport size.
        if (def.width <= 0 || def.height <= 0) return null

        val scrollX = if (def.scrollX) {
            HorizontalScrollData(def.scrollBarOnStartX, def.scrollBarThicknessX)
                .apply {
                    setScrollSpeed(def.scrollSpeed)
                    setCancelScrollEdge(def.cancelScrollEdge)
                }
        } else {
            null
        }

        val scrollY = if (def.scrollY) {
            VerticalScrollData(def.scrollBarOnStartY, def.scrollBarThicknessY)
                .apply {
                    setScrollSpeed(def.scrollSpeed)
                    setCancelScrollEdge(def.cancelScrollEdge)
                }
        } else {
            null
        }

        val container = PMScrollContainerWidget(scrollX = scrollX, scrollY = scrollY)
            .pos(def.x, def.y)
            .size(def.width, def.height)

        def.children.forEach { childDef ->
            val widget = buildChild(childDef)
            if (widget != null) {
                container.child(widget)
            }
        }

        return container
    }
}
