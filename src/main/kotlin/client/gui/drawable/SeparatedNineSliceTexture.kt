package github.kasuminova.prototypemachinery.client.gui.drawable

import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.screen.viewport.GuiContext
import com.cleanroommc.modularui.theme.WidgetTheme
import com.cleanroommc.modularui.utils.Platform
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.util.ResourceLocation

/**
 * A 9-slice drawable similar to ModularUI's [com.cleanroommc.modularui.drawable.AdaptableUITexture],
 * but it can skip the 1px separator lines that exist between the 3x3 slices in gui_states *_expand textures.
 *
 * The intended layout for each axis is:
 *   [cap][separator(1px)][stretch(>=1px)][separator(1px)][cap]
 *
 * We keep the stretching rules (only stretch the middle region), but we do NOT draw the separator pixels.
 */
public class SeparatedNineSliceTexture(
    location: ResourceLocation,
    private val u0: Float,
    private val v0: Float,
    private val u1: Float,
    private val v1: Float,
    private val imageWidth: Int,
    private val imageHeight: Int,
    private val bl: Int,
    private val bt: Int,
    private val br: Int,
    private val bb: Int,
    /** Separator thickness in pixels (normally 1 for gui_states). */
    private val separatorPx: Int = 1,
    private val nonOpaque: Boolean = false
) : IDrawable {

    private val location: ResourceLocation = normalizeLocation(location)

    private fun normalizeLocation(loc: ResourceLocation): ResourceLocation {
        // Mimic ModularUI's UITexture normalization behavior:
        // - Ensure path starts with "textures/"
        // - Ensure it ends with ".png"
        val path = loc.path
        val hasPngSuffix = path.endsWith(".png")
        val hasTexturesPrefix = path.startsWith("textures/")
        if (hasPngSuffix && hasTexturesPrefix) return loc

        val newPath = buildString {
            if (!hasTexturesPrefix) append("textures/")
            append(path)
            if (!hasPngSuffix) append(".png")
        }
        return ResourceLocation(loc.namespace, newPath)
    }

    override fun draw(context: GuiContext, x: Int, y: Int, width: Int, height: Int, widgetTheme: WidgetTheme) {
        // ModularUI textures are not theme-colored by default; ensure GL is reset to white.
        applyColor(widgetTheme.color)
        draw(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())
    }

    private fun draw(x: Float, y: Float, width: Float, height: Float) {
        if (width <= 0f || height <= 0f) return

        // No borders -> plain draw.
        if (bl <= 0 && bt <= 0 && br <= 0 && bb <= 0) {
            GuiDraw.drawTexture(location, x, y, x + width, y + height, u0, v0, u1, v1, nonOpaque)
            return
        }

        Platform.setupDrawTex(nonOpaque)
        Minecraft.getMinecraft().renderEngine.bindTexture(location)

        val uStep = 1f / imageWidth
        val vStep = 1f / imageHeight

        val uBl = bl * uStep
        val uBr = br * uStep
        val vBt = bt * vStep
        val vBb = bb * vStep

        val x1 = x + width
        val y1 = y + height

        val uInnerStart = u0 + uBl
        val vInnerStart = v0 + vBt
        val uInnerEnd = u1 - uBr
        val vInnerEnd = v1 - vBb

        val sepU = (separatorPx.coerceAtLeast(0)) * uStep
        val sepV = (separatorPx.coerceAtLeast(0)) * vStep

        // We skip the separator pixels located on the INNER side of each border.
        // Left/top separators are the last column/row inside bl/bt.
        // Right/bottom separators are the first column/row inside br/bb.
        val leftCapPx = (bl - separatorPx).coerceAtLeast(0)
        val rightCapPx = (br - separatorPx).coerceAtLeast(0)
        val topCapPx = (bt - separatorPx).coerceAtLeast(0)
        val bottomCapPx = (bb - separatorPx).coerceAtLeast(0)

        val uLeftEnd = if (bl > 0 && separatorPx > 0) (uInnerStart - sepU) else uInnerStart
        val vTopEnd = if (bt > 0 && separatorPx > 0) (vInnerStart - sepV) else vInnerStart
        val uRightStart = if (br > 0 && separatorPx > 0) (uInnerEnd + sepU) else uInnerEnd
        val vBottomStart = if (bb > 0 && separatorPx > 0) (vInnerEnd + sepV) else vInnerEnd

        val leftW = leftCapPx.toFloat().coerceAtMost(width)
        val rightW = rightCapPx.toFloat().coerceAtMost((width - leftW).coerceAtLeast(0f))
        val topH = topCapPx.toFloat().coerceAtMost(height)
        val bottomH = bottomCapPx.toFloat().coerceAtMost((height - topH).coerceAtLeast(0f))

        val centerW = (width - leftW - rightW).coerceAtLeast(0f)
        val centerH = (height - topH - bottomH).coerceAtLeast(0f)

        Platform.startDrawing(Platform.DrawMode.QUADS, Platform.VertexFormat.POS_TEX) { buffer ->
            // Special-case: only horizontal borders (slider lr_base_expand)
            if ((bl > 0 || br > 0) && bt <= 0 && bb <= 0) {
                if (leftW > 0f) {
                    GuiDraw.drawTexture(buffer, x, y, x + leftW, y1, u0, v0, uLeftEnd, v1, 0f)
                }
                if (rightW > 0f) {
                    GuiDraw.drawTexture(buffer, x1 - rightW, y, x1, y1, uRightStart, v0, u1, v1, 0f)
                }
                if (centerW > 0f) {
                    GuiDraw.drawTexture(buffer, x + leftW, y, x1 - rightW, y1, uInnerStart, v0, uInnerEnd, v1, 0f)
                }
                return@startDrawing
            }

            // Special-case: only vertical borders
            if (bl <= 0 && br <= 0) {
                if (topH > 0f) {
                    GuiDraw.drawTexture(buffer, x, y, x1, y + topH, u0, v0, u1, vTopEnd, 0f)
                }
                if (bottomH > 0f) {
                    GuiDraw.drawTexture(buffer, x, y1 - bottomH, x1, y1, u0, vBottomStart, u1, v1, 0f)
                }
                if (centerH > 0f) {
                    GuiDraw.drawTexture(buffer, x, y + topH, x1, y1 - bottomH, u0, vInnerStart, u1, vInnerEnd, 0f)
                }
                return@startDrawing
            }

            // 9-slice
            // corners
            if (leftW > 0f && topH > 0f) {
                GuiDraw.drawTexture(buffer, x, y, x + leftW, y + topH, u0, v0, uLeftEnd, vTopEnd, 0f)
            }
            if (rightW > 0f && topH > 0f) {
                GuiDraw.drawTexture(buffer, x1 - rightW, y, x1, y + topH, uRightStart, v0, u1, vTopEnd, 0f)
            }
            if (leftW > 0f && bottomH > 0f) {
                GuiDraw.drawTexture(buffer, x, y1 - bottomH, x + leftW, y1, u0, vBottomStart, uLeftEnd, v1, 0f)
            }
            if (rightW > 0f && bottomH > 0f) {
                GuiDraw.drawTexture(buffer, x1 - rightW, y1 - bottomH, x1, y1, uRightStart, vBottomStart, u1, v1, 0f)
            }

            // edges
            if (leftW > 0f && centerH > 0f) {
                GuiDraw.drawTexture(buffer, x, y + topH, x + leftW, y1 - bottomH, u0, vInnerStart, uLeftEnd, vInnerEnd, 0f)
            }
            if (centerW > 0f && topH > 0f) {
                GuiDraw.drawTexture(buffer, x + leftW, y, x1 - rightW, y + topH, uInnerStart, v0, uInnerEnd, vTopEnd, 0f)
            }
            if (rightW > 0f && centerH > 0f) {
                GuiDraw.drawTexture(buffer, x1 - rightW, y + topH, x1, y1 - bottomH, uRightStart, vInnerStart, u1, vInnerEnd, 0f)
            }
            if (centerW > 0f && bottomH > 0f) {
                GuiDraw.drawTexture(buffer, x + leftW, y1 - bottomH, x1 - rightW, y1, uInnerStart, vBottomStart, uInnerEnd, v1, 0f)
            }

            // center
            if (centerW > 0f && centerH > 0f) {
                GuiDraw.drawTexture(buffer, x + leftW, y + topH, x1 - rightW, y1 - bottomH, uInnerStart, vInnerStart, uInnerEnd, vInnerEnd, 0f)
            }
        }

        GlStateManager.disableBlend()
        GlStateManager.enableAlpha()
    }
}
