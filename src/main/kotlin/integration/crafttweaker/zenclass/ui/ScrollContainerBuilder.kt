package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.ScrollContainerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for ScrollContainer.
 * 可滚动容器构建器。
 */
@ZenClass("mods.prototypemachinery.ui.ScrollContainerBuilder")
@ZenRegister
public class ScrollContainerBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0

    private var scrollX: Boolean = false
    private var scrollY: Boolean = true

    private var scrollBarOnStartX: Boolean = false
    private var scrollBarOnStartY: Boolean = false

    private var scrollBarThicknessX: Int = -1
    private var scrollBarThicknessY: Int = -1

    private var scrollSpeed: Int = 30
    private var cancelScrollEdge: Boolean = true

    private val children = mutableListOf<IWidgetBuilder>()

    @ZenMethod
    override fun setPos(x: Int, y: Int): ScrollContainerBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ScrollContainerBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setScrollX(enabled: Boolean): ScrollContainerBuilder {
        this.scrollX = enabled
        return this
    }

    @ZenMethod
    public fun setScrollY(enabled: Boolean): ScrollContainerBuilder {
        this.scrollY = enabled
        return this
    }

    @ZenMethod
    public fun setScrollSpeed(pixelsPerTick: Int): ScrollContainerBuilder {
        this.scrollSpeed = pixelsPerTick
        return this
    }

    @ZenMethod
    public fun setCancelScrollEdge(cancel: Boolean): ScrollContainerBuilder {
        this.cancelScrollEdge = cancel
        return this
    }

    @ZenMethod
    public fun setScrollBarOnStartX(onStart: Boolean): ScrollContainerBuilder {
        this.scrollBarOnStartX = onStart
        return this
    }

    @ZenMethod
    public fun setScrollBarOnStartY(onStart: Boolean): ScrollContainerBuilder {
        this.scrollBarOnStartY = onStart
        return this
    }

    @ZenMethod
    public fun setScrollBarThicknessX(thickness: Int): ScrollContainerBuilder {
        this.scrollBarThicknessX = thickness
        return this
    }

    @ZenMethod
    public fun setScrollBarThicknessY(thickness: Int): ScrollContainerBuilder {
        this.scrollBarThicknessY = thickness
        return this
    }

    @ZenMethod
    public fun addChild(child: IWidgetBuilder): ScrollContainerBuilder {
        children.add(child)
        return this
    }

    override fun build(): WidgetDefinition {
        return ScrollContainerDefinition(
            x = x,
            y = y,
            width = width,
            height = height,
            scrollX = scrollX,
            scrollY = scrollY,
            scrollBarOnStartX = scrollBarOnStartX,
            scrollBarOnStartY = scrollBarOnStartY,
            scrollBarThicknessX = scrollBarThicknessX,
            scrollBarThicknessY = scrollBarThicknessY,
            scrollSpeed = scrollSpeed,
            cancelScrollEdge = cancelScrollEdge,
            children = children.map { it.build() }
        )
    }
}
