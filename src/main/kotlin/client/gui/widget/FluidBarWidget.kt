package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.client.gui.util.NumberFormatUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fluids.FluidStack

/**
 * A simple vertical fluid bar renderer.
 * - Renders from bottom to top.
 * - Avoids stretching by tiling the fluid sprite.
 */
public class FluidBarWidget(
    private val fluidSupplier: () -> FluidStack?,
    private val amountSupplier: () -> Long,
    private val capacitySupplier: () -> Long
) : Widget<FluidBarWidget>() {

    override fun onInit() {
        super.onInit()
        // size should be configured by caller

        // Dynamic tooltip: fluid name + amount/capacity
        tooltipDynamic { tooltip ->
            val cap = capacitySupplier().coerceAtLeast(0L)
            val amount = amountSupplier().coerceAtLeast(0L)
            val fluid = fluidSupplier()

            val name = if (fluid == null || amount <= 0L) {
                "-"
            } else {
                // In 1.12 this is safe to call here; tooltip is rendered client-side.
                fluid.localizedName
            }

            tooltip.addLine(IKey.str(name))
            tooltip.addLine(IKey.str(NumberFormatUtil.formatMbPairGrouped(amount, cap)))
        }
            .tooltipShowUpTimer(0)
            .tooltipAutoUpdate(true)
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val cap = capacitySupplier().coerceAtLeast(0L)
        if (cap <= 0L) return

        val amount = amountSupplier().coerceIn(0L, cap)
        val heightPx = area.height
        if (heightPx <= 0) return

        val filled = ((amount.toDouble() / cap.toDouble()) * heightPx.toDouble()).toInt().coerceIn(0, heightPx)
        if (filled <= 0) return

        val fluid = fluidSupplier()?.copy()?.also {
            if (it.amount <= 0) it.amount = 1
        } ?: return

        val startY = heightPx - filled
        drawFluidTiled(fluid, 0, startY, area.width, filled)
    }

    private fun drawFluidTiled(fluidStack: FluidStack, x: Int, y: Int, w: Int, h: Int) {
        val fluid = fluidStack.fluid ?: return
        val still: ResourceLocation = fluid.getStill(fluidStack) ?: return

        val mc = Minecraft.getMinecraft()
        val sprite: TextureAtlasSprite = mc.textureMapBlocks.getAtlasSprite(still.toString())

        mc.textureManager.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)

        val color = fluid.getColor(fluidStack)
        val r = ((color shr 16) and 0xFF) / 255f
        val g = ((color shr 8) and 0xFF) / 255f
        val b = (color and 0xFF) / 255f

        GlStateManager.disableLighting()
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.color(r, g, b, 1f)

        // Tile the 16x16 sprite without stretching.
        val tileSize = 16
        var yOff = 0
        while (yOff < h) {
            val drawH = minOf(tileSize, h - yOff)
            var xOff = 0
            while (xOff < w) {
                val drawW = minOf(tileSize, w - xOff)

                val drawX = x + xOff
                // Render from bottom to top.
                val drawY = y + (h - yOff - drawH)

                drawSpritePart(sprite, drawX, drawY, drawW, drawH)
                xOff += tileSize
            }
            yOff += tileSize
        }

        GlStateManager.color(1f, 1f, 1f, 1f)
        GlStateManager.disableBlend()
        GlStateManager.enableDepth()
    }

    private fun drawSpritePart(sprite: TextureAtlasSprite, x: Int, y: Int, w: Int, h: Int) {
        val u0 = sprite.minU
        val u1 = sprite.maxU
        val v0 = sprite.minV
        val v1 = sprite.maxV

        val du = (u1 - u0) * (w / 16f)
        val dv = (v1 - v0) * (h / 16f)

        val tess = Tessellator.getInstance()
        val buf = tess.buffer
        buf.begin(7, DefaultVertexFormats.POSITION_TEX)
        buf.pos(x.toDouble(), (y + h).toDouble(), 0.0).tex(u0.toDouble(), (v0 + dv).toDouble()).endVertex()
        buf.pos((x + w).toDouble(), (y + h).toDouble(), 0.0).tex((u0 + du).toDouble(), (v0 + dv).toDouble()).endVertex()
        buf.pos((x + w).toDouble(), y.toDouble(), 0.0).tex((u0 + du).toDouble(), v0.toDouble()).endVertex()
        buf.pos(x.toDouble(), y.toDouble(), 0.0).tex(u0.toDouble(), v0.toDouble()).endVertex()
        tess.draw()
    }
}
