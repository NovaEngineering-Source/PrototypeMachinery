package github.kasuminova.prototypemachinery.client.gui.builder.factory

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.widget.Widget
import com.cleanroommc.modularui.widgets.ProgressWidget
import com.cleanroommc.modularui.widgets.TextWidget
import github.kasuminova.prototypemachinery.api.ui.definition.DynamicTextDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ImageDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.ProgressBarDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.TextDefinition
import github.kasuminova.prototypemachinery.api.ui.definition.WidgetDefinition
import github.kasuminova.prototypemachinery.client.gui.builder.UIBuildContext
import net.minecraft.util.ResourceLocation
import kotlin.math.roundToInt

public class DisplayWidgetFactory : WidgetFactory {

    override fun build(def: WidgetDefinition, ctx: UIBuildContext, buildChild: (WidgetDefinition) -> IWidget?): IWidget? {
        return when (def) {
            is TextDefinition -> buildText(def)
            is DynamicTextDefinition -> buildDynamicText(def, ctx)
            is ProgressBarDefinition -> buildProgressBar(def, ctx)
            is ImageDefinition -> buildImage(def, ctx)
            else -> null
        }
    }

    private fun buildText(def: TextDefinition): IWidget {
        val text = TextWidget(def.textKey ?: "")
            .pos(def.x, def.y)
            .color(def.color)
            .shadow(def.shadow)

        if (def.width > 0 || def.height > 0) {
            text.size(def.width.coerceAtLeast(1), def.height.coerceAtLeast(1))
        }

        text.alignment(parseAlignment(def.alignment))
        return text
    }

    private fun buildDynamicText(def: DynamicTextDefinition, ctx: UIBuildContext): IWidget {
        val rawKey = ctx.bindings.bindingKey(def.textKey)
        val muiKey = if (rawKey != null) {
            val sync = ctx.bindings.ensureStringBinding(ctx.syncManager, ctx.machineTile, rawKey).syncValue
            IKey.dynamic {
                val raw = sync.stringValue
                val pattern = def.formatPattern
                if (pattern.isNullOrBlank()) {
                    raw
                } else {
                    runCatching { String.format(pattern, raw) }.getOrElse { raw }
                }
            }
        } else {
            IKey.str(def.textKey ?: "")
        }

        val text = TextWidget(muiKey)
            .pos(def.x, def.y)
            .color(def.color)
            .shadow(def.shadow)

        if (def.width > 0 || def.height > 0) {
            text.size(def.width.coerceAtLeast(1), def.height.coerceAtLeast(1))
        }

        text.alignment(parseAlignment(def.alignment))
        return text
    }

    private fun buildProgressBar(def: ProgressBarDefinition, ctx: UIBuildContext): IWidget {
        val direction = when (def.direction.uppercase()) {
            "RIGHT" -> ProgressWidget.Direction.RIGHT
            "LEFT" -> ProgressWidget.Direction.LEFT
            "UP" -> ProgressWidget.Direction.UP
            "DOWN" -> ProgressWidget.Direction.DOWN
            else -> ProgressWidget.Direction.RIGHT
        }

        val progress = ProgressWidget()
            .pos(def.x, def.y)
            .size(def.width, def.height)
            .direction(direction)

        val imageSize = when (direction) {
            ProgressWidget.Direction.UP, ProgressWidget.Direction.DOWN -> def.height
            else -> def.width
        }.coerceAtLeast(1)

        val texturePath = ctx.textures.normalizeTexturePath(def.texture)
        if (texturePath != null) {
            // For custom texture, assume the empty/full bar are stacked vertically in one image.
            val texture = UITexture.fullImage(ResourceLocation(texturePath))
            progress.texture(texture, imageSize)
        } else {
            progress.texture(ctx.textures.defaultProgressEmpty, ctx.textures.defaultProgressFull, imageSize)
        }

        val progressSync = ctx.bindings.bindingKey(def.progressKey)?.let { rawKey ->
            val res = ctx.bindings.ensureDoubleBindingExpr(ctx.syncManager, ctx.machineTile, rawKey)
            progress.syncHandler(ctx.bindings.doubleSyncKey(rawKey), 0)
            res.syncValue
        }

        def.tooltipTemplate?.takeIf { it.isNotBlank() }?.let { template ->
            if (progressSync != null) {
                progress.tooltipDynamic { tooltip ->
                    val v = progressSync.doubleValue.coerceIn(0.0, 1.0)
                    val percent = v * 100.0
                    val percentInt = percent.roundToInt()
                    val percentStr = "$percentInt%"

                    val text = runCatching {
                        // Provide a few common args; unused args are ignored by String.format.
                        String.format(template, v, percent, percentInt, percentStr)
                    }.getOrElse { template }

                    if (text.isNotBlank()) {
                        tooltip.addStringLines(text.split('\n'))
                    }
                }
            } else {
                progress.addTooltipStringLines(template.split('\n'))
            }
        }

        return progress
    }

    private fun buildImage(def: ImageDefinition, ctx: UIBuildContext): IWidget {
        val texture = ctx.textures.parseTexture(def.texture)
        return Widget()
            .pos(def.x, def.y)
            .size(def.width, def.height)
            .background(texture)
    }

    private fun parseAlignment(alignment: String): Alignment {
        return when (alignment.trim().uppercase()) {
            "LEFT" -> Alignment.CenterLeft
            "CENTER" -> Alignment.Center
            "RIGHT" -> Alignment.CenterRight
            "TOP_LEFT" -> Alignment.TopLeft
            "TOP_CENTER" -> Alignment.TopCenter
            "TOP_RIGHT" -> Alignment.TopRight
            "BOTTOM_LEFT" -> Alignment.BottomLeft
            "BOTTOM_CENTER" -> Alignment.BottomCenter
            "BOTTOM_RIGHT" -> Alignment.BottomRight
            else -> Alignment.CenterLeft
        }
    }
}
