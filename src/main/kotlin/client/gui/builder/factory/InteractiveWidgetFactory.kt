package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.widget.ParentWidget
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.SliderWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import github.kasuminova.prototypemachinery.api.ui.definition.ButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.SliderDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ToggleButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext
import github.kasuminova.prototypemachinery.common.network.NetworkHandler
import github.kasuminova.prototypemachinery.common.network.PacketMachineAction

public class InteractiveWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is ButtonDefinition -> buildButton(def, ctx)
            is ToggleButtonDefinition -> buildToggleButton(def, ctx)
            is SliderDefinition -> buildSlider(def, ctx)
            else -> null
        }
    }

    private fun buildButton(def: ButtonDefinition, ctx: UIBuildContext): IWidget {
        @Suppress("UNCHECKED_CAST")
        val button = ButtonWidget()
        button.pos(def.x, def.y)
        button.size(def.width, def.height)

        // Default look from states.png
        button.background(ctx.textures.defaultButtonNormal)
        button.hoverBackground(ctx.textures.defaultButtonHover)

        if (def.text != null) {
            button.child(TextWidget(def.text).alignment(Alignment.Center))
        }

        if (def.actionKey != null) {
            button.onMousePressed { _: Int ->
                NetworkHandler.INSTANCE.sendToServer(
                    PacketMachineAction(ctx.machineTile.pos, def.actionKey)
                )
                true
            }
        }

        return button
    }

    private fun buildToggleButton(def: ToggleButtonDefinition, ctx: UIBuildContext): IWidget {
        val toggle = ToggleButton()
            .size(def.width, def.height)

        val offPath = ctx.textures.normalizeTexturePath(def.textureOff)
        val onPath = ctx.textures.normalizeTexturePath(def.textureOn)
        val offTexture = offPath?.let(ctx.textures::parseTexture) ?: ctx.textures.defaultButtonNormal
        val onTexture = onPath?.let(ctx.textures::parseTexture) ?: ctx.textures.defaultButtonPressed
        toggle.background(offTexture)
        toggle.hoverBackground(if (offPath != null) offTexture else ctx.textures.defaultButtonHover)
        toggle.selectedBackground(onTexture)
        toggle.selectedHoverBackground(if (onPath != null) onTexture else ctx.textures.defaultButtonHoverPressed)

        ctx.bindings.bindingKey(def.stateKey)?.let { rawKey ->
            ctx.bindings.ensureBoolBinding(ctx.syncManager, ctx.machineTile, rawKey)
            toggle.syncHandler(ctx.bindings.boolSyncKey(rawKey), 0)
        }

        // Optional label text (implemented via wrapper, because ToggleButton is not a ParentWidget)
        val textOn = def.textOn
        val textOff = def.textOff
        if (textOn.isNullOrBlank() && textOff.isNullOrBlank()) {
            return toggle.pos(def.x, def.y)
        }

        val wrapper = ParentWidget()
            .pos(def.x, def.y)
            .size(def.width, def.height)

        val onLabel = (textOn ?: "").trim()
        val offLabel = (textOff ?: "").trim()
        val labelKey = IKey.dynamic {
            val selected = toggle.isValueSelected
            val s = if (selected) onLabel else offLabel
            if (s.isNotEmpty()) s else if (selected) "ON" else "OFF"
        }
        val label = TextWidget(labelKey)
            .pos(0, 0)
            .size(def.width, def.height)
            .alignment(Alignment.Center)
            .shadow(false)

        wrapper.child(toggle.pos(0, 0))
        wrapper.child(label)
        return wrapper
    }

    private fun buildSlider(def: SliderDefinition, ctx: UIBuildContext): IWidget {
        val slider = SliderWidget()
            .pos(def.x, def.y)
            .size(def.width, def.height)
            .bounds(def.min, def.max)

        // Default look from states.png
        slider.background(ctx.textures.defaultProgressEmpty)
        slider.sliderTexture(ctx.textures.defaultSliderHandle)
        slider.sliderSize(ctx.textures.sliderHandleW, ctx.textures.sliderHandleH)

        if (def.step > 0) {
            slider.stopper(def.step)
        }

        ctx.bindings.bindingKey(def.valueKey)?.let { rawKey ->
            ctx.bindings.ensureDoubleBinding(ctx.syncManager, ctx.machineTile, rawKey)
            slider.syncHandler(ctx.bindings.doubleSyncKey(rawKey), 0)
        }

        return slider
    }
}
