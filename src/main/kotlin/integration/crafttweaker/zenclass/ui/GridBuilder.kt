package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.GridDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for Grid layout - arranges children in a grid pattern.
 * 网格布局构建器 - 按网格模式排列子组件。
 */
@ZenClass("mods.prototypemachinery.ui.GridBuilder")
@ZenRegister
public class GridBuilder() : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var columns: Int = 1
    private var rows: Int = 1
    private var rowSpacing: Int = 0
    private var columnSpacing: Int = 0
    private val children = mutableListOf<IWidgetBuilder>()

    // Secondary constructor for backward compatibility
    public constructor(columns: Int) : this() {
        this.columns = columns
    }

    @ZenMethod
    override fun setPos(x: Int, y: Int): GridBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): GridBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setSpacing(rowSpacing: Int, columnSpacing: Int): GridBuilder {
        this.rowSpacing = rowSpacing
        this.columnSpacing = columnSpacing
        return this
    }

    /**
     * Set the number of columns.
     * 设置列数。
     */
    @ZenMethod
    public fun setColumns(columns: Int): GridBuilder {
        this.columns = columns
        return this
    }

    /**
     * Set the number of rows.
     * 设置行数。
     */
    @ZenMethod
    public fun setRows(rows: Int): GridBuilder {
        this.rows = rows
        return this
    }

    @ZenMethod
    public fun addChild(child: IWidgetBuilder): GridBuilder {
        children.add(child)
        return this
    }

    override fun build(): WidgetDefinition {
        return GridDefinition(x, y, width, height, columns, rowSpacing, columnSpacing, children.map { it.build() })
    }
}
