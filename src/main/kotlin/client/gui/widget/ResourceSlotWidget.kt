package github.kasuminova.prototypemachinery.client.gui.widget

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.drawable.GuiDraw
import com.cleanroommc.modularui.screen.RichTooltip
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.utils.Color
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.api.key.PMKey
import github.kasuminova.prototypemachinery.client.gui.sync.ResourceSlotSyncHandler
import github.kasuminova.prototypemachinery.common.util.NumberFormatter
import github.kasuminova.prototypemachinery.impl.key.item.PMItemKey
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper

/**
 * # ResourceSlotWidget - PMKey Resource Slot Widget
 * # ResourceSlotWidget - PMKey 资源槽位组件
 *
 * A widget for displaying and interacting with PMKey-based resources.
 * Supports Long quantities and custom rendering.
 *
 * 用于显示和交互基于 PMKey 的资源的组件。
 * 支持 Long 数量和自定义渲染。
 */
public class ResourceSlotWidget<K : PMKey<*>> : Widget<ResourceSlotWidget<K>>(), Interactable {

    public companion object {
        public const val SIZE: Int = 18
    }

    private var syncHandler: ResourceSlotSyncHandler<K>? = null
    private var slotIndex: Int = 0
    private var background: IDrawable? = null
    private var canInsert: Boolean = true
    private var canExtract: Boolean = true

    init {
        size(SIZE)
        tooltip().setAutoUpdate(true)
        tooltipBuilder { tooltip ->
            val key = getCurrentResource() ?: return@tooltipBuilder
            buildTooltip(key, tooltip)
        }
    }

    /**
     * Sets the sync handler for this slot.
     * 设置此槽位的同步处理器。
     */
    public fun syncHandler(handler: ResourceSlotSyncHandler<K>): ResourceSlotWidget<K> {
        this.syncHandler = handler
        return this
    }

    /**
     * Sets the slot index within the storage.
     * 设置存储中的槽位索引。
     */
    public fun slotIndex(index: Int): ResourceSlotWidget<K> {
        this.slotIndex = index
        return this
    }

    /**
     * Sets the background drawable.
     * 设置背景可绘制对象。
     */
    public fun slotBackground(background: IDrawable?): ResourceSlotWidget<K> {
        this.background = background
        return this
    }

    /**
     * Sets whether items can be inserted.
     * 设置是否可以插入物品。
     */
    public fun canInsert(canInsert: Boolean): ResourceSlotWidget<K> {
        this.canInsert = canInsert
        return this
    }

    /**
     * Sets whether items can be extracted.
     * 设置是否可以提取物品。
     */
    public fun canExtract(canExtract: Boolean): ResourceSlotWidget<K> {
        this.canExtract = canExtract
        return this
    }

    override fun onInit() {
        super.onInit()
        size(SIZE)
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        // Draw background
        background?.draw(context, 0, 0, area.width, area.height, widgetTheme.theme)

        val resource = getCurrentResource() ?: return

        // Draw the resource
        drawResource(context, resource)

        // Draw hover overlay
        if (isHovering) {
            GlStateManager.colorMask(true, true, true, false)
            GuiDraw.drawRect(1f, 1f, 16f, 16f, Color.withAlpha(Color.WHITE.main, 0.5f))
            GlStateManager.colorMask(true, true, true, true)
        }
    }

    private fun drawResource(context: ModularGuiContext, resource: K) {
        when (resource) {
            is PMItemKey -> drawItemResource(resource)
            // Add other resource type handlers here
        }

        // Draw count overlay
        val count = resource.count
        if (count > 1) {
            drawCountOverlay(count)
        }
    }

    private fun drawItemResource(key: PMItemKey) {
        val stack = key.uniqueKey.createStack(1)
        if (stack.isEmpty) return

        RenderHelper.enableGUIStandardItemLighting()
        val mc = Minecraft.getMinecraft()
        val itemRender = mc.renderItem

        GlStateManager.pushMatrix()
        GlStateManager.translate(1f, 1f, 0f)
        itemRender.renderItemAndEffectIntoGUI(stack, 0, 0)
        GlStateManager.popMatrix()

        RenderHelper.disableStandardItemLighting()
    }

    private fun drawCountOverlay(count: Long) {
        val text = NumberFormatter.formatCompact(count)
        val mc = Minecraft.getMinecraft()
        val fontRenderer = mc.fontRenderer

        GlStateManager.pushMatrix()
        GlStateManager.translate(0f, 0f, 200f)

        val textWidth = fontRenderer.getStringWidth(text)
        val x = 17 - textWidth
        val y = 9

        // Draw shadow
        fontRenderer.drawString(text, x + 1f, y + 1f, 0x000000, false)
        // Draw text
        fontRenderer.drawString(text, x.toFloat(), y.toFloat(), 0xFFFFFF, false)

        GlStateManager.popMatrix()
    }

    private fun buildTooltip(key: K, tooltip: RichTooltip) {
        when (key) {
            is PMItemKey -> buildItemTooltip(key, tooltip)
        }
    }

    private fun buildItemTooltip(key: PMItemKey, tooltip: RichTooltip) {
        val stack = key.uniqueKey.createStack(1)
        if (stack.isEmpty) return

        tooltip.addFromItem(stack)

        // Add count info for large quantities
        if (key.asPMKey().count > stack.maxStackSize) {
            tooltip.addLine(IKey.str(""))
            tooltip.addLine(
                IKey.lang(
                    "prototypemachinery.gui.resource_slot.count",
                    NumberFormatter.formatWithCommas(key.asPMKey().count)
                )
            )
        }
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        val handler = syncHandler ?: return Interactable.Result.IGNORE

        // Handle click interaction
        handler.handleClick(slotIndex, mouseButton, Interactable.hasShiftDown(), canInsert, canExtract)

        return Interactable.Result.SUCCESS
    }

    override fun onMouseScroll(direction: UpOrDown, amount: Int): Boolean {
        val handler = syncHandler ?: return false

        // Handle scroll interaction (for phantom slots or quantity adjustment)
        // Map UpOrDown to integer direction (1 for UP, -1 for DOWN)
        val dir = if (direction == UpOrDown.UP) 1 else -1
        handler.handleScroll(slotIndex, dir, Interactable.hasShiftDown())

        return true
    }

    private fun getCurrentResource(): K? {
        return syncHandler?.getResourceAt(slotIndex)
    }

}
