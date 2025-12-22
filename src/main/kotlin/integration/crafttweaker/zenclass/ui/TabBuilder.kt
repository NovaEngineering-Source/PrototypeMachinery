package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.SpacerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TabDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for a single Tab.
 * 单个标签页构建器。
 */
@ZenClass("mods.prototypemachinery.ui.TabBuilder")
@ZenRegister
public class TabBuilder(private val tabKey: String, private val title: String) : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var icon: String? = null
    private var content: IWidgetBuilder? = null

    @ZenMethod
    override fun setPos(x: Int, y: Int): TabBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): TabBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setIcon(icon: String?): TabBuilder {
        this.icon = icon
        return this
    }

    @ZenMethod
    public fun setContent(content: IWidgetBuilder): TabBuilder {
        this.content = content
        return this
    }

    override fun build(): WidgetDefinition {
        val builtContent = content?.build() ?: SpacerDefinition(0, 0, 0, 0)
        return TabDefinition(
            x = x,
            y = y,
            width = width,
            height = height,
            tabKey = tabKey,
            title = title,
            icon = icon,
            content = builtContent
        )
    }
}
