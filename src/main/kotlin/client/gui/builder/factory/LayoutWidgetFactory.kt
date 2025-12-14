package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.widget.ParentWidget
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.layout.Row
import github.kasuminova.prototypemachinery.api.ui.definition.ColumnDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.GridDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.RowDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext
import github.kasuminova.prototypemachinery.client.gui.widget.PMSmoothGrid

public class LayoutWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is PanelDefinition -> buildNestedPanel(def, ctx, buildChild)
            is RowDefinition -> buildRow(def, buildChild)
            is ColumnDefinition -> buildColumn(def, buildChild)
            is GridDefinition -> buildGrid(def, buildChild)
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
        val column = Column()
            .pos(def.x, def.y)
            .size(def.width, def.height)

        val bgPath = ctx.textures.normalizeTexturePath(def.backgroundTexture)
        if (bgPath != null) {
            column.background(ctx.textures.parseTexture(bgPath))
        }

        def.children.forEach { childDef ->
            val widget = buildChild(childDef)
            if (widget != null) {
                column.child(widget)
            }
        }

        return column
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
}
