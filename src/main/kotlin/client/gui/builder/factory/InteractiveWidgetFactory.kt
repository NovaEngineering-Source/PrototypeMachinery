package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.GuiAxis
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.widget.ParentWidget
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.SliderWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget
import github.kasuminova.prototypemachinery.api.ui.definition.ButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.SliderDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TextFieldDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ToggleButtonDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.DynamicDrawable
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext
import github.kasuminova.prototypemachinery.client.gui.widget.PMTriStateTextureButton
import github.kasuminova.prototypemachinery.common.network.NetworkHandler
import github.kasuminova.prototypemachinery.common.network.PacketMachineAction

public class InteractiveWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is ButtonDefinition -> buildButton(def, ctx)
            is ToggleButtonDefinition -> buildToggleButton(def, ctx)
            is SliderDefinition -> buildSlider(def, ctx)
            is TextFieldDefinition -> buildTextField(def, ctx)
            else -> null
        }
    }

    private fun buildButton(def: ButtonDefinition, ctx: UIBuildContext): IWidget {
        val skin = def.skin?.trim()?.takeIf { it.isNotEmpty() }
        if (skin != null && skin.startsWith("gui_states/")) {
            val (normal, hover, pressed, disabled) = resolveGuiStatesButtonSkin(ctx, skin)
            val onClick = def.actionKey?.let { key ->
                {
                    NetworkHandler.INSTANCE.sendToServer(
                        PacketMachineAction(ctx.machineTile.pos, key)
                    )
                }
            }
            val button = PMTriStateTextureButton(
                normal = normal,
                hover = hover,
                pressed = pressed,
                disabled = disabled,
                onClick = onClick
            )
                .pos(0, 0)
                .size(def.width, def.height)

            // Optional label: wrap because Widget doesn't support child(...)
            if (def.text != null) {
                val wrapper = ParentWidget()
                    .pos(def.x, def.y)
                    .size(def.width, def.height)
                wrapper.child(button)
                wrapper.child(
                    TextWidget(def.text)
                        .pos(0, 0)
                        .size(def.width, def.height)
                        .alignment(Alignment.Center)
                )
                return wrapper
            }

            return button.pos(def.x, def.y)
        }

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

    private data class ButtonSkin4(
        val normal: IDrawable,
        val hover: IDrawable,
        val pressed: IDrawable,
        val disabled: IDrawable
    )

    private fun resolveGuiStatesButtonSkin(ctx: UIBuildContext, skin: String): ButtonSkin4 {
        // Note: gui_states assets are under `textures/gui/gui_states/...`.
        // We use full-image for fixed variants and adaptable for *_expand variants.
        return when (skin) {
            // Empty button (fixed 16x19)
            "gui_states/empty_button/normal" -> {
                ButtonSkin4(
                    normal = ctx.textures.guiStatesFull("gui/gui_states/empty_button/normal/default_n"),
                    hover = ctx.textures.guiStatesFull("gui/gui_states/empty_button/normal/selected_n"),
                    pressed = ctx.textures.guiStatesFull("gui/gui_states/empty_button/normal/pressed_n"),
                    disabled = ctx.textures.guiStatesFull("gui/gui_states/empty_button/normal/disable_n")
                )
            }
            "gui_states/empty_button/shadow" -> {
                ButtonSkin4(
                    normal = ctx.textures.guiStatesFull("gui/gui_states/empty_button/shadow/default_n"),
                    hover = ctx.textures.guiStatesFull("gui/gui_states/empty_button/shadow/selected_n"),
                    pressed = ctx.textures.guiStatesFull("gui/gui_states/empty_button/shadow/pressed_n"),
                    disabled = ctx.textures.guiStatesFull("gui/gui_states/empty_button/shadow/disable_n")
                )
            }

            // Expand button (9-slice, 16x19)
            "gui_states/expand_button/normal" -> {
                val normal = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/normal/default_expand",
                    imageW = 16,
                    imageH = 19,
                    // gui_states.md: 3x3 片之间有 1px 分隔线，拉伸只发生在中间 1px。
                    // 左边=3px + 1px 分隔线；右边=10px + 1px 分隔线；上/下同理。
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 9
                )
                val hover = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/normal/selected_expand",
                    imageW = 16,
                    imageH = 19,
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 8,
                    // gui_states.md: selected_expand 的 9-slice 内容从 Y=1 开始（Y:1..18），底边高度为 7。
                    subX = 0,
                    subY = 1,
                    subW = 16,
                    subH = 18
                )
                val pressed = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/normal/pressed_expand",
                    imageW = 16,
                    imageH = 19,
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 7,
                    // gui_states.md: pressed_expand 的 9-slice 内容从 Y=2 开始（Y:2..18），底边高度为 6。
                    subX = 0,
                    subY = 2,
                    subW = 16,
                    subH = 17
                )
                val disabled = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/normal/disable_expand",
                    imageW = 16,
                    imageH = 19,
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 8,
                    // gui_states.md: disable_expand 的 9-slice 内容从 Y=1 开始（Y:1..18），底边高度为 7。
                    subX = 0,
                    subY = 1,
                    subW = 16,
                    subH = 18
                )
                ButtonSkin4(normal, hover, pressed, disabled)
            }
            "gui_states/expand_button/shadow" -> {
                val normal = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/shadow/default_expand",
                    imageW = 16,
                    imageH = 19,
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 9
                )
                val hover = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/shadow/selected_expand",
                    imageW = 16,
                    imageH = 19,
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 8,
                    // gui_states.md: selected_expand 的 9-slice 内容从 Y=1 开始（Y:1..18），底边高度为 7。
                    subX = 0,
                    subY = 1,
                    subW = 16,
                    subH = 18
                )
                val pressed = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/shadow/pressed_expand",
                    imageW = 16,
                    imageH = 19,
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 7,
                    // gui_states.md: pressed_expand 的 9-slice 内容从 Y=2 开始（Y:2..18），底边高度为 6。
                    subX = 0,
                    subY = 2,
                    subW = 16,
                    subH = 17
                )
                val disabled = ctx.textures.guiStatesSeparatedAdaptable(
                    relPath = "gui/gui_states/empty_button/shadow/disable_expand",
                    imageW = 16,
                    imageH = 19,
                    borderLeft = 4,
                    borderTop = 9,
                    borderRight = 11,
                    borderBottom = 8,
                    // gui_states.md: disable_expand 的 9-slice 内容从 Y=1 开始（Y:1..18），底边高度为 7。
                    subX = 0,
                    subY = 1,
                    subW = 16,
                    subH = 18
                )
                ButtonSkin4(normal, hover, pressed, disabled)
            }

            else -> {
                // Fallback: keep old look
                ButtonSkin4(
                    normal = ctx.textures.defaultButtonNormal,
                    hover = ctx.textures.defaultButtonHover,
                    pressed = ctx.textures.defaultButtonPressed,
                    disabled = ctx.textures.defaultButtonNormal
                )
            }
        }
    }

    private fun buildToggleButton(def: ToggleButtonDefinition, ctx: UIBuildContext): IWidget {
        val toggle = ToggleButton()
            .size(def.width, def.height)

        val skin = def.skin?.trim()?.takeIf { it.isNotEmpty() }
        if (skin == "gui_states/switch/normal" || skin == "gui_states/switch/shadow") {
            val folder = if (skin.endsWith("/shadow")) "shadow" else "normal"
            val off = ctx.textures.guiStatesFull("gui/gui_states/switch/$folder/off")
            val offSel = ctx.textures.guiStatesFull("gui/gui_states/switch/$folder/off_selected")
            val on = ctx.textures.guiStatesFull("gui/gui_states/switch/$folder/on")
            val onSel = ctx.textures.guiStatesFull("gui/gui_states/switch/$folder/on_selected")
            toggle.background(off)
            toggle.hoverBackground(offSel)
            toggle.selectedBackground(on)
            toggle.selectedHoverBackground(onSel)
        } else {
            val offPath = ctx.textures.normalizeTexturePath(def.textureOff)
            val onPath = ctx.textures.normalizeTexturePath(def.textureOn)
            val offTexture = offPath?.let(ctx.textures::parseTexture) ?: ctx.textures.defaultButtonNormal
            val onTexture = onPath?.let(ctx.textures::parseTexture) ?: ctx.textures.defaultButtonPressed
            toggle.background(offTexture)
            toggle.hoverBackground(if (offPath != null) offTexture else ctx.textures.defaultButtonHover)
            toggle.selectedBackground(onTexture)
            toggle.selectedHoverBackground(if (onPath != null) onTexture else ctx.textures.defaultButtonHoverPressed)
        }

        ctx.bindings.bindingKey(def.stateKey)?.let { rawKey ->
            ctx.bindings.ensureBoolBindingExpr(ctx.syncManager, ctx.machineTile, rawKey)
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

        // Axis
        slider.setAxis(if (def.horizontal) GuiAxis.X else GuiAxis.Y)

        val skin = def.skin?.trim()?.takeIf { it.isNotEmpty() }
        if (skin != null && skin.startsWith("gui_states/")) {
            applyGuiStatesSliderSkin(slider, ctx, skin)
        } else {
            // Default look from states.png
            slider.background(ctx.textures.defaultProgressEmpty)
            slider.sliderTexture(ctx.textures.defaultSliderHandle)
            slider.sliderSize(ctx.textures.sliderHandleW, ctx.textures.sliderHandleH)
        }

        if (def.step > 0) {
            slider.stopper(def.step)
        }

        ctx.bindings.bindingKey(def.valueKey)?.let { rawKey ->
            ctx.bindings.ensureDoubleBindingExpr(ctx.syncManager, ctx.machineTile, rawKey)
            slider.syncHandler(ctx.bindings.doubleSyncKey(rawKey), 0)
        }

        return slider
    }

    private fun applyGuiStatesSliderSkin(slider: SliderWidget, ctx: UIBuildContext, skin: String) {
        // Supported skins:
        // - gui_states/slider/{s|m}/{x|y}/{normal|shadow}
        // - gui_states/slider/{s|m}/{x|y}_expand/{normal|shadow}
        val parts = skin.split('/')
        if (parts.size < 5) {
            slider.background(ctx.textures.defaultProgressEmpty)
            slider.sliderTexture(ctx.textures.defaultSliderHandle)
            slider.sliderSize(ctx.textures.sliderHandleW, ctx.textures.sliderHandleH)
            return
        }

        val size = parts[2] // s | m
        val axisPart = parts[3] // x | y | x_expand | y_expand
        val shadowOrNormal = parts[4] // normal | shadow
        val folder = if (shadowOrNormal == "shadow") "shadow" else "normal"
        val expand = axisPart.endsWith("_expand")
        val axis = axisPart.removeSuffix("_expand")

        val baseRel = when (axis) {
            "x" -> "gui/gui_states/slider/$size/$folder/lr_base" + if (expand) "_expand" else ""
            "y" -> "gui/gui_states/slider/$size/$folder/ud_base" + if (expand) "_expand" else ""
            else -> null
        }

        val handlePrefix = when (axis) {
            "x" -> "lr"
            "y" -> "ud"
            else -> null
        }

        if (baseRel == null || handlePrefix == null) {
            slider.background(ctx.textures.defaultProgressEmpty)
            slider.sliderTexture(ctx.textures.defaultSliderHandle)
            slider.sliderSize(ctx.textures.sliderHandleW, ctx.textures.sliderHandleH)
            return
        }

        val base: IDrawable = if (!expand) {
            ctx.textures.guiStatesFull(baseRel)
        } else {
            // gui_states.md: *_expand 贴图在 3 段之间存在 1px 分隔线；只拉伸中间 1px。
            when ("$size/$axis/$folder") {
                // Horizontal
                "s/x/normal", "s/x/shadow" -> ctx.textures.guiStatesSeparatedAdaptable(baseRel, imageW = 14, imageH = 10, borderLeft = 6, borderTop = 0, borderRight = 7, borderBottom = 0)
                // slider/m/normal/lr_base_expand.png 实际是 17x13
                "m/x/normal" -> ctx.textures.guiStatesSeparatedAdaptable(baseRel, imageW = 17, imageH = 13, borderLeft = 7, borderTop = 0, borderRight = 9, borderBottom = 0)
                // slider/m/shadow/lr_base_expand.png 实际是 14x13（资源本身与 normal 不同）
                "m/x/shadow" -> ctx.textures.guiStatesSeparatedAdaptable(baseRel, imageW = 14, imageH = 13, borderLeft = 6, borderTop = 0, borderRight = 7, borderBottom = 0)

                // Vertical
                "s/y/normal", "s/y/shadow" -> ctx.textures.guiStatesSeparatedAdaptable(baseRel, imageW = 10, imageH = 16, borderLeft = 0, borderTop = 8, borderRight = 0, borderBottom = 7)
                "m/y/normal", "m/y/shadow" -> ctx.textures.guiStatesSeparatedAdaptable(baseRel, imageW = 13, imageH = 18, borderLeft = 0, borderTop = 8, borderRight = 0, borderBottom = 9)

                else -> ctx.textures.guiStatesFull(baseRel)
            }
        }

        val handleNormal = ctx.textures.guiStatesFull("gui/gui_states/slider/$size/${handlePrefix}_default")
        val handleHover = ctx.textures.guiStatesFull("gui/gui_states/slider/$size/${handlePrefix}_selected")
        val handlePressed = ctx.textures.guiStatesFull("gui/gui_states/slider/$size/${handlePrefix}_pressed")

        val (handleW, handleH) = when ("$size/$axis") {
            "s/x", "s/y" -> 7 to 8
            "m/x" -> 11 to 11
            "m/y" -> 10 to 12
            else -> ctx.textures.sliderHandleW to ctx.textures.sliderHandleH
        }

        slider.background(base)

        // Use a single drawable but switch texture by widget state.
        slider.sliderTexture(
            DynamicDrawable {
                when {
                    slider.isDragging -> handlePressed
                    slider.isHovering -> handleHover
                    else -> handleNormal
                }
            }
        )
        slider.sliderSize(handleW, handleH)
    }

    private fun buildTextField(def: TextFieldDefinition, ctx: UIBuildContext): IWidget {
        val tf = TextFieldWidget()
            .pos(def.x, def.y)
            .size(def.width, def.height)

        val skin = def.skin?.trim()?.takeIf { it.isNotEmpty() }
        if (skin != null && skin.startsWith("gui_states/")) {
            when (skin) {
                "gui_states/input_box/normal" -> {
                    // gui_states.md: input_box_normal 贴图尺寸 56x13，输入区域 X:2 Y:2 W:51 H:8。
                    // 为了支持任意尺寸输入框，这里使用 9-slice 拉伸中间区域。
                    val bg = ctx.textures.guiStatesAdaptable(
                        relPath = "gui/gui_states/input_box/box",
                        imageW = 56,
                        imageH = 13,
                        borderLeft = 2,
                        borderTop = 2,
                        borderRight = 3,
                        borderBottom = 3
                    )
                    tf.background(bg)
                    tf.hoverBackground(bg)
                }
                "gui_states/input_box/shadow" -> {
                    val bg = ctx.textures.guiStatesAdaptable(
                        relPath = "gui/gui_states/input_box/box_shadow",
                        imageW = 56,
                        imageH = 13,
                        borderLeft = 2,
                        borderTop = 2,
                        borderRight = 3,
                        borderBottom = 3
                    )
                    tf.background(bg)
                    tf.hoverBackground(bg)
                }
                "gui_states/input_box/expand/normal" -> {
                    val bg = ctx.textures.guiStatesSeparatedAdaptable(
                        relPath = "gui/gui_states/input_box/box_expand",
                        imageW = 8,
                        imageH = 13,
                        // gui_states.md: 9-slice 片之间有 1px 分隔线；只拉伸中间 1px。
                        // 左=2px + 1px 分隔线；右=3px + 1px 分隔线；上/下同理。
                        borderLeft = 3,
                        borderTop = 6,
                        borderRight = 4,
                        borderBottom = 6
                    )
                    tf.background(bg)
                    tf.hoverBackground(bg)
                }
                "gui_states/input_box/expand/shadow" -> {
                    val bg = ctx.textures.guiStatesSeparatedAdaptable(
                        relPath = "gui/gui_states/input_box/box_expand_shadow",
                        imageW = 8,
                        imageH = 13,
                        borderLeft = 3,
                        borderTop = 6,
                        borderRight = 4,
                        borderBottom = 6
                    )
                    tf.background(bg)
                    tf.hoverBackground(bg)
                }
            }
        }

        // Sync binding
        ctx.bindings.bindingKey(def.valueKey)?.let { rawKey ->
            ctx.bindings.ensureStringBinding(ctx.syncManager, ctx.machineTile, rawKey)
            tf.syncHandler(ctx.bindings.stringSyncKey(rawKey), 0)
        }

        // Input mode
        if (def.inputType.trim().lowercase() == "long") {
            val min = def.minLong
            val max = def.maxLong
            tf.setNumbersLong { v ->
                var next = v
                if (min != null) next = next.coerceAtLeast(min)
                if (max != null) next = next.coerceAtMost(max)
                next
            }
        }

        return tf
    }
}
