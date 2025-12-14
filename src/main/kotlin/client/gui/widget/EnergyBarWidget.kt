package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.client.gui.util.NumberFormatUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation

/**
 * A simple vertical energy bar renderer.
 * - Renders from bottom to top.
 * - No background (caller can draw an overlay if needed).
 *
 * 渲染机制与 [FluidBarWidget] 保持一致：
 * - 自行计算填充高度
 * - 从下往上绘制
 * - 使用 Widget.draw() 绘制，不依赖 ProgressWidget
 */
public class EnergyBarWidget(
    /** The GUI texture that contains the energy bar region at (0, 190). */
    private val textureSupplier: () -> ResourceLocation,
    private val energySupplier: () -> Long,
    private val capacitySupplier: () -> Long,
    private val unitSuffix: String = "FE"
) : Widget<EnergyBarWidget>() {

    private companion object {
        private const val TEX_SIZE = 256

        private const val BAR_U_X = 0
        private const val BAR_V_Y = 190
        private const val BAR_W = 16
        private const val BAR_H = 66
    }

    override fun onInit() {
        super.onInit()

        tooltipDynamic { tooltip ->
            val cap = capacitySupplier().coerceAtLeast(0L)
            val energy = energySupplier().coerceAtLeast(0L)

            tooltip.addLine(IKey.str(NumberFormatUtil.formatGrouped(energy.coerceAtMost(cap)) + " / " + NumberFormatUtil.formatGrouped(cap) + " $unitSuffix"))
        }
            .tooltipShowUpTimer(0)
            .tooltipAutoUpdate(true)
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val cap = capacitySupplier().coerceAtLeast(0L)
        if (cap <= 0L) return

        val energy = energySupplier().coerceIn(0L, cap)
        val heightPx = area.height
        val widthPx = area.width
        if (heightPx <= 0 || widthPx <= 0) return

        val filled = ((energy.toDouble() / cap.toDouble()) * heightPx.toDouble()).toInt().coerceIn(0, heightPx)
        if (filled <= 0) return

        val startY = heightPx - filled

        drawTexturedFill(0, startY, widthPx, filled, heightPx)
    }


    /**
     * Draw the filled part using the bar texture region (0, 190, 16, 66) on the hatch GUI texture.
     *
     * We crop vertically based on fill ratio (bottom -> top) instead of drawing a solid color.
     */
    private fun drawTexturedFill(x: Int, y: Int, w: Int, filledH: Int, totalH: Int) {
        val tex = textureSupplier()
        Minecraft.getMinecraft().textureManager.bindTexture(tex)

        val u0 = BAR_U_X.toDouble() / TEX_SIZE.toDouble()
        val u1 = (BAR_U_X + BAR_W).toDouble() / TEX_SIZE.toDouble()

        // Convert fill (in widget pixels) into texture V coordinates in the [BAR_V_Y, BAR_V_Y + BAR_H] region.
        val ratio = (filledH.toDouble() / totalH.toDouble()).coerceIn(0.0, 1.0)
        val v1 = (BAR_V_Y + BAR_H).toDouble() / TEX_SIZE.toDouble()
        val v0 = (BAR_V_Y.toDouble() + (BAR_H.toDouble() * (1.0 - ratio))) / TEX_SIZE.toDouble()

        GlStateManager.disableLighting()
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.color(1f, 1f, 1f, 1f)

        val tess = Tessellator.getInstance()
        val buf = tess.buffer
        buf.begin(7, DefaultVertexFormats.POSITION_TEX)
        buf.pos(x.toDouble(), (y + filledH).toDouble(), 0.0).tex(u0, v1).endVertex()
        buf.pos((x + w).toDouble(), (y + filledH).toDouble(), 0.0).tex(u1, v1).endVertex()
        buf.pos((x + w).toDouble(), y.toDouble(), 0.0).tex(u1, v0).endVertex()
        buf.pos(x.toDouble(), y.toDouble(), 0.0).tex(u0, v0).endVertex()
        tess.draw()

        GlStateManager.disableBlend()
        GlStateManager.enableDepth()
    }
}
