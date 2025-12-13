package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.PanelDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.ui.PanelBuilder")
@ZenRegister
public class PanelBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 176
    private var height: Int = 166
    private var backgroundTexture: String? = null
    private val children = mutableListOf<IWidgetBuilder>()

    @ZenMethod
    override fun setPos(x: Int, y: Int): PanelBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): PanelBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setBackground(texture: String?): PanelBuilder {
        this.backgroundTexture = texture
        return this
    }

    @ZenMethod
    public fun addChild(child: IWidgetBuilder): PanelBuilder {
        children.add(child)
        return this
    }

    override fun build(): WidgetDefinition {
        return PanelDefinition(
            x, y, width, height,
            backgroundTexture,
            children.map { it.build() }
        )
    }
}
