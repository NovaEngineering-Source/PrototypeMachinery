package github.kasuminova.prototypemachinery.integration.crafttweaker.zenclass.ui

import crafttweaker.annotations.ZenRegister
import github.kasuminova.prototypemachinery.api.ui.definition.ImageDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import stanhebben.zenscript.annotations.ZenClass
import stanhebben.zenscript.annotations.ZenMethod

/**
 * Builder for Image widget - displays a texture/image.
 * 图像组件构建器 - 显示纹理/图像。
 */
@ZenClass("mods.prototypemachinery.ui.ImageBuilder")
@ZenRegister
public class ImageBuilder(private val texture: String) : IWidgetBuilder {
    private var x: Int = 0
    private var y: Int = 0
    private var width: Int = 16
    private var height: Int = 16

    @ZenMethod
    override fun setPos(x: Int, y: Int): ImageBuilder {
        this.x = x
        this.y = y
        return this
    }

    @ZenMethod
    override fun setSize(width: Int, height: Int): ImageBuilder {
        this.width = width
        this.height = height
        return this
    }

    override fun build(): WidgetDefinition {
        return ImageDefinition(x, y, width, height, texture)
    }
}
