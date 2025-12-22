package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.ToggleButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for ToggleButton widget - a button that toggles between on/off states.
 * 切换按钮组件构建器 - 在开/关状态之间切换的按钮。
 */
@ZenClass("mods.prototypemachinery.ui.ToggleButtonBuilder")
@ZenRegister
public class ToggleButtonBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 27
    private var height: Int = 15
    private var skin: String? = null
    private var stateKey: String? = null
    private var textOn: String? = null
    private var textOff: String? = null
    private var textureOn: String? = null
    private var textureOff: String? = null

    @ZenMethod
    override fun setPos(x: Int, y: Int): ToggleButtonBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ToggleButtonBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun bindState(key: String?): ToggleButtonBuilder {
        this.stateKey = key
        return this
    }

    @ZenMethod
    public fun setTextOn(text: String?): ToggleButtonBuilder {
        this.textOn = text
        return this
    }

    @ZenMethod
    public fun setTextOff(text: String?): ToggleButtonBuilder {
        this.textOff = text
        return this
    }

    @ZenMethod
    public fun setTextures(textureOn: String?, textureOff: String?): ToggleButtonBuilder {
        this.textureOn = textureOn
        this.textureOff = textureOff
        return this
    }

    /**
     * Set the texture when toggle is ON.
     * 设置开启状态时的纹理。
     */
    @ZenMethod
    public fun setOnTexture(texture: String?): ToggleButtonBuilder {
        this.textureOn = texture
        return this
    }

    /**
     * Set the texture when toggle is OFF.
     * 设置关闭状态时的纹理。
     */
    @ZenMethod
    public fun setOffTexture(texture: String?): ToggleButtonBuilder {
        this.textureOff = texture
        return this
    }

    /**
     * Set visual skin id (e.g. gui_states templates).
     * 设置视觉皮肤 ID（例如 gui_states 模板）。
     */
    @ZenMethod
    public fun setSkin(skin: String?): ToggleButtonBuilder {
        this.skin = skin
        return this
    }

    override fun build(): WidgetDefinition {
        return ToggleButtonDefinition(x, y, width, height, skin, stateKey, textOn, textOff, textureOn, textureOff)
    }
}
