package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.TextFieldDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for TextField widget.
 * 文本输入框组件构建器。
 */
@ZenClass("mods.prototypemachinery.ui.TextFieldBuilder")
@ZenRegister
public class TextFieldBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 56
    private var height: Int = 13

    private var valueKey: String? = null
    private var inputType: String = "string" // "string" | "long"
    private var minLong: Long? = null
    private var maxLong: Long? = null
    private var skin: String? = null

    @ZenMethod
    override fun setPos(x: Int, y: Int): TextFieldBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): TextFieldBuilder {
        this.width = width
        this.height = height
        return this
    }

    /** Bind the text field to a string binding key. */
    @ZenMethod
    public fun bindValue(key: String?): TextFieldBuilder {
        this.valueKey = key
        return this
    }

    /**
     * Set input type.
     * - "string" : free text
     * - "long"   : numeric (long)
     */
    @ZenMethod
    public fun setInputType(inputType: String): TextFieldBuilder {
        this.inputType = inputType
        return this
    }

    /** Set numeric clamp range (only used when inputType == "long"). */
    @ZenMethod
    public fun setLongRange(min: Long, max: Long): TextFieldBuilder {
        this.minLong = min
        this.maxLong = max
        return this
    }

    /** Set visual skin id (e.g. gui_states templates). */
    @ZenMethod
    public fun setSkin(skin: String?): TextFieldBuilder {
        this.skin = skin
        return this
    }

    override fun build(): WidgetDefinition {
        return TextFieldDefinition(
            x = x,
            y = y,
            width = width,
            height = height,
            valueKey = valueKey,
            inputType = inputType,
            minLong = minLong,
            maxLong = maxLong,
            skin = skin
        )
    }
}
