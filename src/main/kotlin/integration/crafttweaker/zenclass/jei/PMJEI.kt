package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.jei

import crafttweaker.annotations.ZenRegister
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * ZenScript helper factory for building JEI layouts.
 */
@ZenClass("mods.prototypemachinery.jei.PMJEI")
@ZenRegister
public object PMJEI {

    @ZenMethod
    @JvmStatic
    public fun createLayout(): LayoutBuilder {
        return LayoutBuilder()
    }

    @ZenMethod
    @JvmStatic
    public fun createLayoutSized(width: Int, height: Int): LayoutBuilder {
        return LayoutBuilder().setSize(width, height)
    }
}
