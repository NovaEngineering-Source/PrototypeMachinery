package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.SliderDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for Slider widget - numeric input via sliding.
 * 滑块组件构建器 - 通过滑动输入数值。
 */
@ZenClass("mods.prototypemachinery.ui.SliderBuilder")
@ZenRegister
public class SliderBuilder : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 100
    private var height: Int = 14
    private var skin: String? = null
    private var min: Double = 0.0
    private var max: Double = 100.0
    private var step: Double = 1.0
    private var valueKey: String? = null
    private var horizontal: Boolean = true

    @ZenMethod
    override fun setPos(x: Int, y: Int): SliderBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): SliderBuilder {
        this.width = width
        this.height = height
        return this
    }

    @ZenMethod
    public fun setBounds(min: Double, max: Double): SliderBuilder {
        this.min = min
        this.max = max
        return this
    }

    /**
     * Alias for setBounds - set the value range.
     * setBounds 的别名 - 设置值范围。
     */
    @ZenMethod
    public fun setRange(min: Double, max: Double): SliderBuilder {
        return setBounds(min, max)
    }

    @ZenMethod
    public fun setStep(step: Double): SliderBuilder {
        this.step = step
        return this
    }

    @ZenMethod
    public fun bindValue(key: String?): SliderBuilder {
        this.valueKey = key
        return this
    }

    @ZenMethod
    public fun setHorizontal(horizontal: Boolean): SliderBuilder {
        this.horizontal = horizontal
        return this
    }

    /**
     * Set visual skin id (e.g. gui_states templates).
     * 设置视觉皮肤 ID（例如 gui_states 模板）。
     */
    @ZenMethod
    public fun setSkin(skin: String?): SliderBuilder {
        this.skin = skin
        return this
    }

    override fun build(): WidgetDefinition {
        return SliderDefinition(x, y, width, height, skin, min, max, step, valueKey, horizontal)
    }
}
