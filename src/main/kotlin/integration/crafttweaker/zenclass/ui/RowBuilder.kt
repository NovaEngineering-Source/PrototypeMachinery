package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.RowDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for Row layout - arranges children horizontally.
 * 行布局构建器 - 水平排列子组件。
 */
@ZenClass("mods.prototypemachinery.ui.RowBuilder")
@ZenRegister
public class RowBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var spacing: Int = 0
    private val children = mutableListOf<IWidgetBuilder>()

    @ZenMethod
    override fun setPos(x: Int, y: Int): RowBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): RowBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setSpacing(spacing: Int): RowBuilder {
        this.spacing = spacing
        return this
    }

    @ZenMethod
    public fun addChild(child: IWidgetBuilder): RowBuilder {
        children.add(child)
        return this
    }

    override fun build(): WidgetDefinition {
        return RowDefinition(x, y, width, height, spacing, children.map { it.build() })
    }
}
