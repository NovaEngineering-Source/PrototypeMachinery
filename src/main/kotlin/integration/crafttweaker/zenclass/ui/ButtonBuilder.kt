package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.ButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.ui.ButtonBuilder")
@ZenRegister
public class ButtonBuilder(private val text: String?) : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 27
    private var height: Int = 15
    private var actionKey: String? = null

    @ZenMethod
    override fun setPos(x: Int, y: Int): ButtonBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ButtonBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun onClick(actionKey: String): ButtonBuilder {
        this.actionKey = actionKey
        return this
    }

    override fun build(): WidgetDefinition {
        return ButtonDefinition(x, y, width, height, text, actionKey)
    }
}
