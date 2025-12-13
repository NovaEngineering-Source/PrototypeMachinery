package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.TextDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.ui.TextBuilder")
@ZenRegister
public class TextBuilder(private val textKey: String?) : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0 // Auto-size by default
    private var height: Int = 0
    private var color: Int = 0xFFFFFF
    private var shadow: Boolean = true
    private var alignment: String = "LEFT"

    @ZenMethod
    override fun setPos(x: Int, y: Int): TextBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): TextBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setColor(color: Int): TextBuilder {
        this.color = color
        return this
    }

    @ZenMethod
    public fun setShadow(shadow: Boolean): TextBuilder {
        this.shadow = shadow
        return this
    }

    @ZenMethod
    public fun setAlignment(alignment: String): TextBuilder {
        this.alignment = alignment
        return this
    }

    override fun build(): WidgetDefinition {
        return TextDefinition(x, y, width, height, textKey, color, shadow, alignment)
    }
}
