package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.DynamicTextDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.SpacerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TooltipAreaDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for DynamicText widget - text intended to update from a data binding.
 * 动态文本组件构建器 - 预期从数据绑定更新的文本。
 */
@ZenClass("mods.prototypemachinery.ui.DynamicTextBuilder")
@ZenRegister
public class DynamicTextBuilder(private var textKey: String?) : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var formatPattern: String? = null
    private var color: Int = 0xFFFFFF
    private var shadow: Boolean = true
    private var alignment: String = "LEFT"

    @ZenMethod
    override fun setPos(x: Int, y: Int): DynamicTextBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): DynamicTextBuilder {
        this.width = width
        this.height = height
        return this
    }

    /**
     * Set (or clear) the binding key.
     * 设置（或清空）绑定 key。
     */
    @ZenMethod
    public fun setKey(key: String?): DynamicTextBuilder {
        this.textKey = key
        return this
    }

    /**
     * Set a printf-like format, e.g. "Energy: %d FE".
     * 设置类似 printf 的格式串，例如 "Energy: %d FE"。
     */
    @ZenMethod
    public fun setFormat(pattern: String?): DynamicTextBuilder {
        this.formatPattern = pattern
        return this
    }

    @ZenMethod
    public fun setColor(color: Int): DynamicTextBuilder {
        this.color = color
        return this
    }

    @ZenMethod
    public fun setShadow(shadow: Boolean): DynamicTextBuilder {
        this.shadow = shadow
        return this
    }

    @ZenMethod
    public fun setAlignment(alignment: String): DynamicTextBuilder {
        this.alignment = alignment
        return this
    }

    override fun build(): WidgetDefinition {
        return DynamicTextDefinition(
            x = x,
            y = y,
            width = width,
            height = height,
            textKey = textKey,
            formatPattern = formatPattern,
            color = color,
            shadow = shadow,
            alignment = alignment
        )
    }
}

/**
 * Builder for Spacer widget - empty space for layout purposes.
 * 空白占位组件构建器 - 用于布局空隙。
 */
@ZenClass("mods.prototypemachinery.ui.SpacerBuilder")
@ZenRegister
public class SpacerBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0

    @ZenMethod
    override fun setPos(x: Int, y: Int): SpacerBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): SpacerBuilder {
        this.width = width
        this.height = height
        return this
    }

    override fun build(): WidgetDefinition {
        return SpacerDefinition(x, y, width, height)
    }
}

/**
 * Builder for TooltipArea widget - invisible hover area with tooltips.
 * 工具提示区域构建器 - 不可见的悬停区域，用于显示提示。
 */
@ZenClass("mods.prototypemachinery.ui.TooltipAreaBuilder")
@ZenRegister
public class TooltipAreaBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private val tooltipLines: MutableList<String> = mutableListOf()

    @ZenMethod
    override fun setPos(x: Int, y: Int): TooltipAreaBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): TooltipAreaBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun addLine(line: String): TooltipAreaBuilder {
        this.tooltipLines.add(line)
        return this
    }

    @ZenMethod
    public fun clearLines(): TooltipAreaBuilder {
        this.tooltipLines.clear()
        return this
    }

    @ZenMethod
    public fun setLines(lines: Array<String>): TooltipAreaBuilder {
        this.tooltipLines.clear()
        this.tooltipLines.addAll(lines)
        return this
    }

    override fun build(): WidgetDefinition {
        return TooltipAreaDefinition(x, y, width, height, tooltipLines.toList())
    }
}
