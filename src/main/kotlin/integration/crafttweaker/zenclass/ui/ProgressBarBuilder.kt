package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.ProgressBarDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.ui.ProgressBarBuilder")
@ZenRegister
public class ProgressBarBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 20
    private var height: Int = 10
    private var direction: String = "RIGHT"
    private var texture: String? = null
    private var progressKey: String? = null
    private var tooltipTemplate: String? = null

    @ZenMethod
    override fun setPos(x: Int, y: Int): ProgressBarBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ProgressBarBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setDirection(direction: String): ProgressBarBuilder {
        this.direction = direction
        return this
    }

    @ZenMethod
    public fun setTexture(texture: String?): ProgressBarBuilder {
        this.texture = texture
        return this
    }

    @ZenMethod
    public fun bindProgress(key: String?): ProgressBarBuilder {
        this.progressKey = key
        return this
    }

    @ZenMethod
    public fun setTooltip(template: String?): ProgressBarBuilder {
        this.tooltipTemplate = template
        return this
    }

    override fun build(): WidgetDefinition {
        return ProgressBarDefinition(x, y, width, height, direction, texture, progressKey, tooltipTemplate)
    }
}
