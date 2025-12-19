package github.kasuminova.prototypemachinery.integration.jei.builtin.widget

import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO

/**
 * Simple 9-slice background widget for JEI panels.
 *
 * - Corners keep their original pixels.
 * - Edges stretch in one direction.
 * - Center stretches in both directions.
 */
public class JeiNineSliceBackgroundWidget(
    private val texture: ResourceLocation,
    /**
     * Corner size in pixels. If <= 0, a best-effort value will be inferred from the texture.
     */
    private val cornerPx: Int = 0,
    /**
     * If true, fill the center area with a sampled solid color instead of stretching the texture center.
     * This avoids ugly artifacts when the source center is not designed for scaling.
     */
    private val fillCenter: Boolean = true,
    /**
     * How to split the source texture.
     * - AUTO_PIXELS: infer a reasonable border thickness (or use [cornerPx]).
     * - THIRDS: split the source into 3x3 equal regions.
     */
    private val splitMode: SplitMode = SplitMode.AUTO_PIXELS,
) : Widget<JeiNineSliceBackgroundWidget>() {

    public enum class SplitMode {
        AUTO_PIXELS,
        THIRDS,
    }

    public companion object {
        private data class TexInfo(
            val w: Int,
            val h: Int,
            val centerArgb: Int,
            val inferredCorner: Int,
        )

        private val infoCache: MutableMap<ResourceLocation, TexInfo> = ConcurrentHashMap()

        private fun argbNoAlpha(rgb: Int): Int {
            // ImageIO returns ARGB; many MC textures are RGB-only. Force alpha=FF.
            return (rgb or (0xFF shl 24))
        }

        private fun colorDistanceSq(a: Int, b: Int): Int {
            val ar = (a shr 16) and 0xFF
            val ag = (a shr 8) and 0xFF
            val ab = a and 0xFF
            val br = (b shr 16) and 0xFF
            val bg = (b shr 8) and 0xFF
            val bb = b and 0xFF
            val dr = ar - br
            val dg = ag - bg
            val db = ab - bb
            return dr * dr + dg * dg + db * db
        }

        private fun inferCorner(img: java.awt.image.BufferedImage, center: Int): Int {
            val w = img.width.coerceAtLeast(1)
            val h = img.height.coerceAtLeast(1)
            val midX = w / 2
            val midY = h / 2

            // Threshold: allow a bit of noise/gradient.
            val thr = 18 * 18

            fun scanFromLeft(): Int {
                for (x in 0 until midX) {
                    val c = argbNoAlpha(img.getRGB(x, midY))
                    if (colorDistanceSq(c, center) <= thr) return x
                }
                return midX
            }

            fun scanFromTop(): Int {
                for (y in 0 until midY) {
                    val c = argbNoAlpha(img.getRGB(midX, y))
                    if (colorDistanceSq(c, center) <= thr) return y
                }
                return midY
            }

            // Use max to be safe (avoid including border graphics into stretchable center).
            val left = scanFromLeft()
            val top = scanFromTop()
            return maxOf(2, left, top)
        }

        private fun getTextureInfo(tex: ResourceLocation): TexInfo {
            return infoCache.getOrPut(tex) {
                try {
                    val mc = Minecraft.getMinecraft()
                    mc.resourceManager.getResource(tex).inputStream.use { input ->
                        val img = ImageIO.read(input)
                        val w = (img?.width ?: 256).coerceAtLeast(1)
                        val h = (img?.height ?: 256).coerceAtLeast(1)

                        val cx = w / 2
                        val cy = h / 2
                        val center = if (img != null) argbNoAlpha(img.getRGB(cx, cy)) else 0xFFCCCCCC.toInt()
                        val inferred = if (img != null) inferCorner(img, center) else 6

                        TexInfo(w = w, h = h, centerArgb = center, inferredCorner = inferred)
                    }
                } catch (_: Throwable) {
                    TexInfo(w = 256, h = 256, centerArgb = 0xFFCCCCCC.toInt(), inferredCorner = 6)
                }
            }
        }

        private fun drawSlice(
            tex: ResourceLocation,
            dx: Float,
            dy: Float,
            dw: Float,
            dh: Float,
            sx0: Int,
            sy0: Int,
            sx1: Int,
            sy1: Int,
            sw: Int,
            sh: Int,
        ) {
            if (dw <= 0f || dh <= 0f) return
            val u0 = sx0.toFloat() / sw.toFloat()
            val v0 = sy0.toFloat() / sh.toFloat()
            val u1 = sx1.toFloat() / sw.toFloat()
            val v1 = sy1.toFloat() / sh.toFloat()
            // NOTE: GuiDraw.drawTexture expects x0,y0,x1,y1 (end coordinates), NOT width/height.
            // Passing dw/dh directly breaks the right/bottom slices and can cause border pixels to be stretched into the background.
            GuiDraw.drawTexture(tex, dx, dy, dx + dw, dy + dh, u0, v0, u1, v1)
        }
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val info = getTextureInfo(texture)
        val sw = info.w
        val sh = info.h

        val tw = area.width.coerceAtLeast(1)
        val th = area.height.coerceAtLeast(1)

        val (cx, cy) = when (splitMode) {
            SplitMode.THIRDS -> {
                // Equal ratio split: 1/3 of source width/height.
                val rx = (sw / 3).coerceAtLeast(1)
                val ry = (sh / 3).coerceAtLeast(1)
                rx.coerceAtMost(tw / 2) to ry.coerceAtMost(th / 2)
            }
            SplitMode.AUTO_PIXELS -> {
                val desiredCorner = if (cornerPx > 0) cornerPx else info.inferredCorner
                desiredCorner.coerceAtLeast(0).coerceAtMost(minOf(sw / 2, tw / 2)) to
                    desiredCorner.coerceAtLeast(0).coerceAtMost(minOf(sh / 2, th / 2))
            }
        }

        val midW = (tw - cx * 2).coerceAtLeast(0)
        val midH = (th - cy * 2).coerceAtLeast(0)

        // Source coordinates in px
        val sx0 = 0
        val sx1 = cx
        val sx2 = sw - cx
        val sx3 = sw

        val sy0 = 0
        val sy1 = cy
        val sy2 = sh - cy
        val sy3 = sh

        // Dest coordinates in px (widget local)
        val dx0 = 0f
        val dx1 = cx.toFloat()
        val dx2 = (cx + midW).toFloat()
        val dx3 = tw.toFloat()

        val dy0 = 0f
        val dy1 = cy.toFloat()
        val dy2 = (cy + midH).toFloat()
        val dy3 = th.toFloat()

        // Top row
        drawSlice(texture, dx0, dy0, (dx1 - dx0), (dy1 - dy0), sx0, sy0, sx1, sy1, sw, sh) // TL
        drawSlice(texture, dx1, dy0, (dx2 - dx1), (dy1 - dy0), sx1, sy0, sx2, sy1, sw, sh) // T
        drawSlice(texture, dx2, dy0, (dx3 - dx2), (dy1 - dy0), sx2, sy0, sx3, sy1, sw, sh) // TR

        // Middle row
        drawSlice(texture, dx0, dy1, (dx1 - dx0), (dy2 - dy1), sx0, sy1, sx1, sy2, sw, sh) // L
        if (fillCenter) {
            // Solid fill for the center to avoid scaling artifacts.
            GuiDraw.drawRect(dx1, dy1, (dx2 - dx1), (dy2 - dy1), info.centerArgb)
        } else {
            drawSlice(texture, dx1, dy1, (dx2 - dx1), (dy2 - dy1), sx1, sy1, sx2, sy2, sw, sh) // C
        }
        drawSlice(texture, dx2, dy1, (dx3 - dx2), (dy2 - dy1), sx2, sy1, sx3, sy2, sw, sh) // R

        // Bottom row
        drawSlice(texture, dx0, dy2, (dx1 - dx0), (dy3 - dy2), sx0, sy2, sx1, sy3, sw, sh) // BL
        drawSlice(texture, dx1, dy2, (dx2 - dx1), (dy3 - dy2), sx1, sy2, sx2, sy3, sw, sh) // B
        drawSlice(texture, dx2, dy2, (dx3 - dx2), (dy3 - dy2), sx2, sy2, sx3, sy3, sw, sh) // BR
    }
}
