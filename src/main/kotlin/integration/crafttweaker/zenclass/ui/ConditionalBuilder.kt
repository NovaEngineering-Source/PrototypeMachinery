package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.ConditionalDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for Conditional wrapper.
 * 条件包装器构建器。
 *
 * Notes:
 * - This wraps any child widget definition.
 * - `visibleIf` / `enabledIf` are evaluated by runtime bindings.
 */
@ZenClass("mods.prototypemachinery.ui.ConditionalBuilder")
@ZenRegister
public class ConditionalBuilder(private val child: IWidgetBuilder) : IWidgetBuilder {
    private var visibleIf: String? = null
    private var enabledIf: String? = null

    @ZenMethod
    public fun setVisibleIf(expr: String?): ConditionalBuilder {
        this.visibleIf = expr
        return this
    }

    @ZenMethod
    public fun setEnabledIf(expr: String?): ConditionalBuilder {
        this.enabledIf = expr
        return this
    }

    @ZenMethod
    override fun setPos(x: Int, y: Int): ConditionalBuilder {
        child.setPos(x, y)
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ConditionalBuilder {
        child.setSize(width, height)
        return this
    }

    override fun build(): WidgetDefinition {
        return ConditionalDefinition(content = child.build(), visibleIf = visibleIf, enabledIf = enabledIf)
    }
}
