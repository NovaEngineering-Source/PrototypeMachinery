package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

@ZenClass("mods.prototypemachinery.ui.IWidgetBuilder")
@ZenRegister
public interface IWidgetBuilder {
    @ZenMethod
    public fun setPos(x: Int, y: Int): IWidgetBuilder

    @ZenMethod
    public fun setSize(width: Int, height: Int): IWidgetBuilder

    // Internal build method
    public fun build(): WidgetDefinition
}
