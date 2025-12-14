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
import net.minecraft.item.ItemStack

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
        // NOTE: we detect double-tap but we do NOT delay the single click anymore.
        private const val DOUBLE_TAP_WINDOW_MS: Long = 250L
    }

    private var syncHandler: ResourceSlotSyncHandler<K>? = null
    private var slotIndex: Int = 0
    private var background: IDrawable? = null
    private var canInsert: Boolean = true
    private var canExtract: Boolean = true

    // Double-tap detection without delaying single click.
    private var lastTapAtMs: Long = -1L
    private var lastTapButton: Int = -1
    private var ignoreNextTap: Boolean = false

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

        // Draw the resource (if present)
        val resource = getCurrentResource()
        if (resource != null) {
            drawResource(context, resource)
        }

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

        val handler = syncHandler
        if (handler != null) {
            tooltip.addLine(IKey.str(""))
            tooltip.addLine(
                IKey.lang(
                    "prototypemachinery.gui.resource_slot.types",
                    handler.getUsedTypes(),
                    handler.getMaxTypes()
                )
            )
            tooltip.addLine(
                IKey.lang(
                    "prototypemachinery.gui.resource_slot.stored",
                    NumberFormatter.formatWithCommas(key.asPMKey().count),
                    NumberFormatter.formatWithCommas(handler.getMaxCountPerType())
                )
            )
        } else {
            // Fallback: still show stored count.
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

        val shift = Interactable.hasShiftDown()

        // Vanilla-like behavior:
        // - Left click: extract if cursor is empty (or same item) and extraction is allowed; otherwise insert.
        // - Right click: extract if allowed.
        //
        // This is especially important for INPUT hatches and IO input grids: players expect to be able to
        // click items out, not only insert.
        val mc = Minecraft.getMinecraft()
        val player = mc.player
        val cursor: ItemStack = player?.inventory?.itemStack ?: ItemStack.EMPTY
        val resource = getCurrentResource()

        // Shift-click quick move (vanilla-like): when cursor is empty, move extracted items directly
        // into the player inventory instead of putting them on the cursor.
        if (shift && cursor.isEmpty && canExtract && resource != null) {
            handler.requestQuickMoveExtract(slotIndex, mouseButton)
            // Prevent the subsequent tap callback (on release) from also scheduling an action.
            ignoreNextTap = true
            return Interactable.Result.SUCCESS
        }

        // For normal clicks, accept the interaction on press so ModularUI will call onMouseTapped on release.
        return Interactable.Result.SUCCESS
    }

    @Suppress("KotlinConstantConditions")
    private fun performSingleClick(mouseButton: Int, shift: Boolean) {
        val handler = syncHandler ?: return

        val mc = Minecraft.getMinecraft()
        val player = mc.player
        val cursor: ItemStack = player?.inventory?.itemStack ?: ItemStack.EMPTY
        val resource = getCurrentResource()

        fun canLeftClickExtract(): Boolean {
            if (!canExtract) return false
            // If insertion is not possible (e.g. output-only), prefer extraction.
            if (!canInsert) return resource != null
            if (resource == null) return false
            if (cursor.isEmpty) return true
            // If cursor holds the same item, allow stacking extraction like vanilla.
            if (resource is PMItemKey) {
                val template = resource.uniqueKey.createStack(1)
                return !template.isEmpty && ItemStack.areItemStacksEqual(cursor, template)
            }
            return false
        }

        when (mouseButton) {
            0 -> {
                if (canLeftClickExtract()) {
                    handler.requestExtract(slotIndex, mouseButton, shift)
                    return
                }
                if (canInsert) {
                    handler.requestInsert(slotIndex, mouseButton, shift)
                    return
                }
            }
            1 -> {
                // If the cursor holds an item, right click behaves like vanilla "place one".
                // Otherwise, right click extracts a single item.
                if (!cursor.isEmpty && canInsert) {
                    handler.requestInsert(slotIndex, mouseButton, shift)
                    return
                }
                if (canExtract && resource != null) {
                    handler.requestExtract(slotIndex, mouseButton, shift)
                    return
                }
            }
        }
    }

    override fun onMouseTapped(mouseButton: Int): Interactable.Result {
        val handler = syncHandler ?: return Interactable.Result.IGNORE

        if (ignoreNextTap) {
            ignoreNextTap = false
            return Interactable.Result.SUCCESS
        }

        val now = Minecraft.getSystemTime()
        val shift = Interactable.hasShiftDown()

        val isDoubleTap = (mouseButton == lastTapButton) && (lastTapAtMs > 0L) && (now - lastTapAtMs <= DOUBLE_TAP_WINDOW_MS)
        if (isDoubleTap) {
            // Consume the double tap; reset state so triple-click won't chain weirdly.
            lastTapAtMs = -1L
            lastTapButton = -1
            performDoubleClick(mouseButton)
            return Interactable.Result.SUCCESS
        }

        lastTapAtMs = now
        lastTapButton = mouseButton
        performSingleClick(mouseButton, shift)
        return Interactable.Result.SUCCESS
    }

    private fun performDoubleClick(mouseButton: Int) {
        val handler = syncHandler ?: return

        val mc = Minecraft.getMinecraft()
        val player = mc.player
        val cursor: ItemStack = player?.inventory?.itemStack ?: ItemStack.EMPTY
        val resource = getCurrentResource()

        // Double-click action:
        // - If cursor holds an item and insertion is allowed: insert all matching items from cursor + inventory.
        // - If cursor is empty and extraction is allowed: extract as many as possible of that resource into inventory.
        if (!cursor.isEmpty && canInsert) {
            handler.requestInsertAllSame(slotIndex, mouseButton)
            return
        }
        if (cursor.isEmpty && canExtract && resource != null) {
            handler.requestExtractAllSame(slotIndex, mouseButton)
            return
        }
    }

    override fun onMouseScroll(direction: UpOrDown, amount: Int): Boolean {
        val handler = syncHandler ?: return false

        // IMPORTANT UX NOTE:
        // This widget often lives inside a scrollable Grid (ScrollArea).
        // If we always consume the mouse wheel event here, the parent scroll widget
        // will never receive it, forcing users to scroll only on the scrollbar strip.
        //
        // Therefore:
        // - Normal wheel: bubble up (return false) so the parent Grid can scroll.
        // - Shift + wheel: reserved for per-slot interactions (phantom/quantity adjustment).
        if (!Interactable.hasShiftDown()) {
            return false
        }

        // Handle scroll interaction (for phantom slots or quantity adjustment)
        // Map UpOrDown to integer direction (1 for UP, -1 for DOWN)
        val dir = if (direction == UpOrDown.UP) 1 else -1
        handler.handleScroll(slotIndex, dir, true)

        return true
    }

    private fun getCurrentResource(): K? {
        return syncHandler?.getResourceAt(slotIndex)
    }

}
