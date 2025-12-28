package github.kasuminova.prototypemachinery.client.buildinstrument.widget

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import kotlin.math.max
import kotlin.math.min

/**
 * Material preview widget with drag/scroll functionality.
 */
@SideOnly(Side.CLIENT)
internal class MaterialPreviewWidget(
    private val materialsProvider: () -> List<ItemStack>
) : Widget<MaterialPreviewWidget>(), Interactable {

    companion object {
        private const val CELL_SIZE = 18
        private const val MIN_W = 44
        private const val MIN_H = 24
        private const val ITEM_OFFSET = 2

        private val BG by lazy {
            UITexture.fullImage(
                ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/material_preview")
            )
        }
        private val CELL_BG by lazy {
            UITexture.builder()
                // Use PM's default UI atlas (textures/gui/states.png). The previous path pointed to a missing
                // Build Instrument-local `states.png` and could crash or render as missing-texture purple.
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
                .imageSize(256, 256)
                .subAreaXYWH(74, 11, CELL_SIZE, CELL_SIZE)
                .build()
        }
    }

    private var scrollY: Float = 0f
    private var dragging: Boolean = false
    private var lastDragY: Int = 0

    var onScrollChanged: ((Float) -> Unit)? = null

    override fun onInit() {
        super.onInit()
        excludeAreaInRecipeViewer()
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        BG.draw(0f, 0f, area.w().toFloat(), area.h().toFloat())

        val materials = materialsProvider()
        if (materials.isEmpty()) {
            super.draw(context, widgetTheme)
            return
        }

        val contentW = area.w() - 6
        val contentH = area.h() - 6
        val cols = max(1, contentW / CELL_SIZE)
        val rows = (materials.size + cols - 1) / cols
        val totalContentH = rows * CELL_SIZE
        val maxScroll = max(0, totalContentH - contentH)
        scrollY = scrollY.coerceIn(0f, maxScroll.toFloat())

        GlStateManager.pushMatrix()
        GlStateManager.translate(3f, 3f, 0f)

        val startRow = (scrollY / CELL_SIZE).toInt()
        val endRow = min(rows, startRow + (contentH / CELL_SIZE) + 2)

        RenderHelper.enableGUIStandardItemLighting()
        val mc = Minecraft.getMinecraft()
        val itemRenderer = mc.renderItem

        for (row in startRow until endRow) {
            for (col in 0 until cols) {
                val idx = row * cols + col
                if (idx >= materials.size) break

                val cellX = col * CELL_SIZE
                val cellY = row * CELL_SIZE - scrollY

                if (cellY < -CELL_SIZE || cellY > contentH) continue

                CELL_BG.draw(cellX.toFloat(), cellY, CELL_SIZE.toFloat(), CELL_SIZE.toFloat())

                val stack = materials[idx]
                if (!stack.isEmpty) {
                    GlStateManager.pushMatrix()
                    GlStateManager.translate(cellX.toFloat() + ITEM_OFFSET, cellY + ITEM_OFFSET, 100f)
                    itemRenderer.renderItemAndEffectIntoGUI(stack, 0, 0)
                    itemRenderer.renderItemOverlayIntoGUI(mc.fontRenderer, stack, 0, 0, null)
                    GlStateManager.popMatrix()
                }
            }
        }

        RenderHelper.disableStandardItemLighting()
        GlStateManager.popMatrix()

        super.draw(context, widgetTheme)
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        if (mouseButton != 0) return Interactable.Result.IGNORE

        val relX = context.mouseX - area.x
        val relY = context.mouseY - area.y
        if (relX >= 3 && relX < area.w() - 3 && relY >= 3 && relY < area.h() - 3) {
            dragging = true
            lastDragY = context.mouseY
            return Interactable.Result.SUCCESS
        }

        return Interactable.Result.IGNORE
    }

    override fun onMouseRelease(mouseButton: Int): Boolean {
        if (mouseButton == 0) {
            dragging = false
        }
        return true
    }

    override fun onMouseDrag(mouseButton: Int, timeSinceClick: Long) {
        if (dragging && mouseButton == 0) {
            val dy = context.mouseY - lastDragY
            lastDragY = context.mouseY
            scroll(-dy.toFloat())
        }
    }

    override fun onMouseScroll(direction: UpOrDown, amount: Int): Boolean {
        val delta = if (direction == UpOrDown.UP) -CELL_SIZE else CELL_SIZE
        scroll(delta.toFloat())
        return true
    }

    private fun scroll(delta: Float) {
        val materials = materialsProvider()
        val contentW = area.w() - 6
        val contentH = area.h() - 6
        val cols = max(1, contentW / CELL_SIZE)
        val rows = (materials.size + cols - 1) / cols
        val totalContentH = rows * CELL_SIZE
        val maxScroll = max(0, totalContentH - contentH)

        scrollY = (scrollY + delta).coerceIn(0f, maxScroll.toFloat())
        onScrollChanged?.invoke(if (maxScroll > 0) scrollY / maxScroll else 0f)
    }

    public fun setScrollPercent(percent: Float) {
        val materials = materialsProvider()
        val contentW = area.w() - 6
        val contentH = area.h() - 6
        val cols = max(1, contentW / CELL_SIZE)
        val rows = (materials.size + cols - 1) / cols
        val totalContentH = rows * CELL_SIZE
        val maxScroll = max(0, totalContentH - contentH)

        scrollY = (percent * maxScroll).coerceIn(0f, maxScroll.toFloat())
    }
}
