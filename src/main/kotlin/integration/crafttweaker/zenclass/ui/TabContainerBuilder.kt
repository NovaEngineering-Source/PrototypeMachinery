package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.TabContainerDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TabDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for TabContainer.
 * 标签容器构建器。
 */
@ZenClass("mods.prototypemachinery.ui.TabContainerBuilder")
@ZenRegister
public class TabContainerBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 0
    private var height: Int = 0
    private var tabPosition: String = "LEFT"
    private val tabs = mutableListOf<TabBuilder>()

    @ZenMethod
    override fun setPos(x: Int, y: Int): TabContainerBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): TabContainerBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setTabPosition(pos: String): TabContainerBuilder {
        this.tabPosition = pos
        return this
    }

    @ZenMethod
    public fun addTab(tab: TabBuilder): TabContainerBuilder {
        this.tabs.add(tab)
        return this
    }

    override fun build(): WidgetDefinition {
        val builtTabs: List<TabDefinition> = tabs.mapNotNull { it.build() as? TabDefinition }
        return TabContainerDefinition(
            x = x,
            y = y,
            width = width,
            height = height,
            tabPosition = tabPosition,
            tabs = builtTabs
        )
    }
}
