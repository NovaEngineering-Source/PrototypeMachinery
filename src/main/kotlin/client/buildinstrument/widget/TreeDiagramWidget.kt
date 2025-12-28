package github.kasuminova.prototypemachinery.client.buildinstrument.widget

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.utils.Color
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Gui
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import org.lwjgl.opengl.GL11

/**
 * Tree diagram widget for Build Instrument.
 *
 * Interactions:
 * - Drag header area (Y:0-19) to pan the view.
 * - Scroll wheel: vertical pan.
 * - Ctrl + scroll wheel: zoom in/out.
 * - Shift + scroll wheel: horizontal pan.
 */
@SideOnly(Side.CLIENT)
internal class TreeDiagramWidget(
    private val treeDataProvider: () -> TreeData?,
    /** Called when the user selects a node (or clears selection by clicking empty area). */
    private val onSelectionChanged: ((TreeNode?) -> Unit)? = null
) : Widget<TreeDiagramWidget>(), Interactable {

    companion object {
        private const val HEADER_HEIGHT = 19
        private const val MIN_SCALE = 0.5f
        private const val MAX_SCALE = 2.0f
        private const val SCROLL_SPEED = 12f
        private const val ZOOM_STEP = 0.1f

        private const val TREE_H = 14
        private const val TREE_BG_W = 13
        private const val TREE_RIGHT_W = 16

        // Small horizontal slider (from gui_states.md: slider_s_x expand)
        private const val SLIDER_H = 10f
        private const val SLIDER_HANDLE_W = 7f
        private const val SLIDER_HANDLE_H = 8f
        private const val SLIDER_BASE_L_W = 5
        private const val SLIDER_BASE_M_W = 1
        private const val SLIDER_BASE_R_W = 6

        private fun statesTex(path: String): UITexture {
            return UITexture.fullImage(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_states/$path"))
        }

        private val SLIDER_S_X_BASE_L by lazy {
            UITexture.builder()
                // NOTE: actual png size is 14x10 (see gui_states.md slicing numbers: x=8,w=6).
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_states/slider/s/normal/lr_base_expand"))
                .imageSize(14, 10)
                .subAreaXYWH(0, 0, SLIDER_BASE_L_W, 10)
                .build()
        }
        private val SLIDER_S_X_BASE_M by lazy {
            UITexture.builder()
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_states/slider/s/normal/lr_base_expand"))
                .imageSize(14, 10)
                .subAreaXYWH(6, 0, SLIDER_BASE_M_W, 10)
                .build()
        }
        private val SLIDER_S_X_BASE_R by lazy {
            UITexture.builder()
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_states/slider/s/normal/lr_base_expand"))
                .imageSize(14, 10)
                .subAreaXYWH(8, 0, SLIDER_BASE_R_W, 10)
                .build()
        }

        // NOTE: file prefix is "lr_*" (not "rl_*"), see assets/gui_states/slider/s.
        private val SLIDER_S_X_HANDLE_DEFAULT by lazy { statesTex("slider/s/lr_default") }
        private val SLIDER_S_X_HANDLE_SELECTED by lazy { statesTex("slider/s/lr_selected") }
        private val SLIDER_S_X_HANDLE_PRESSED by lazy { statesTex("slider/s/lr_pressed") }

        private val TREE_BG by lazy {
            UITexture.builder()
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/tree_diagram"))
                .imageSize(32, TREE_H)
                .subAreaXYWH(0, 0, TREE_BG_W, TREE_H)
                .build()
        }

        private val TREE_MID by lazy {
            UITexture.builder()
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/tree_diagram"))
                .imageSize(32, TREE_H)
                .subAreaXYWH(14, 0, 1, TREE_H)
                .build()
        }

        private val TREE_RIGHT by lazy {
            UITexture.builder()
                .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/tree_diagram"))
                .imageSize(32, TREE_H)
                // x=16 .. 31 (16px)
                .subAreaXYWH(16, 0, TREE_RIGHT_W, TREE_H)
                .build()
        }

        private const val CONN_COLOR = 0xFFF2F2F2.toInt()
        private const val CONN_SHADOW = 0xFF696D88.toInt()

        // Expand / Withdraw button (gui_build_instrument.md: ec_button, W:11 H:12)
        private const val EC_W = 11f
        private const val EC_H = 12f

        private fun ecTex(name: String): UITexture {
            return UITexture.fullImage(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_build_instrument/button/$name"))
        }

        private val EC_EXPAND_DEFAULT by lazy { ecTex("expand_default") }
        private val EC_EXPAND_SELECTED by lazy { ecTex("expand_selected") }
        private val EC_EXPAND_PRESSED by lazy { ecTex("expand_pressed") }

        private val EC_WITHDRAW_DEFAULT by lazy { ecTex("withdraw_default") }
        private val EC_WITHDRAW_SELECTED by lazy { ecTex("withdraw_selected") }
        private val EC_WITHDRAW_PRESSED by lazy { ecTex("withdraw_pressed") }

        private val PANEL_BG: Int = 0x1EFFFFFF
        private val PANEL_BORDER: Int = 0x66999DB5
        private val PANEL_BORDER_EXPANDED: Int = 0x99A6ABC6.toInt()
        private const val OPTION_TEXT = 0xFFE6E6E6.toInt()
        private const val OPTION_TEXT_DIM = 0xFFA0A0A0.toInt()
    }

    private var panX: Float = 0f
    private var panY: Float = 0f
    private var scale: Float = 1f

    private var viewInitializedForRootId: String? = null

    private var pressedToggleNodeId: String? = null

    private var draggingSliderNodeId: String? = null

    private fun ensureGuiTexturedState() {
        // Gui.drawRect / other draw calls may change GL state.
        GlStateManager.enableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GlStateManager.color(1f, 1f, 1f, 1f)
    }

    private var selectedNodeId: String? = null
    private val expandedById: MutableMap<String, Boolean> = mutableMapOf()
    private val hitboxes: MutableList<NodeHitbox> = mutableListOf()

    private var dragging: Boolean = false
    private var draggingButton: Int = -1
    private var dragStartX: Int = 0
    private var dragStartY: Int = 0
    private var dragStartPanX: Float = 0f
    private var dragStartPanY: Float = 0f

    /** Simple tree data model. */
    public data class TreeNode(
        val id: String,
        val label: String,
        val type: NodeType,
        /** Optional: the underlying structure node represented by this tree entry (Build Instrument only). */
        val structure: MachineStructure? = null,
        /** Optional: the base origin passed into StructurePreviewBuilder for this node (before applying node.offset). */
        val baseOrigin: BlockPos = BlockPos.ORIGIN,
        /** Optional: which structure instance this UI node belongs to (used for slice override lookup). */
        val structurePath: String? = null,
        /** Optional: highlight only blocks whose requirement stableKey equals this value. */
        val highlightRequirementKey: String? = null,
        /** Optional: item icon rendered on the left (used by material list). */
        val iconStack: ItemStack? = null,
        /** Optional: right-side action button label (used by material option rows). */
        val actionLabel: String? = null,
        /** Whether the right-side action button is enabled. */
        val actionEnabled: Boolean = true,
        /** Optional slider spec for slice-count / layer selector nodes. */
        val slider: SliderSpec? = null,
        /** Optional callback when [slider] changes. */
        val onSliderChanged: ((Int) -> Unit)? = null,
        /** Optional callback when this node is activated (clicked). Used by UI-only nodes (e.g. material choice). */
        val onActivate: (() -> Unit)? = null,
        val children: List<TreeNode> = emptyList(),
        var expanded: Boolean = true,
        var enabled: Boolean = true
    )

    public data class SliderSpec(
        val min: Int,
        val max: Int,
        val value: Int
    )

    public enum class NodeType {
        MAIN,
        SUBSTRUCTURE,
        // UI-only nodes
        MATERIAL_PANEL,
        MATERIAL_OPTION,
        SLICE_SLIDER,
        EXTEND_STRUCTURE,
        EXTEND_LENGTH,
        REPLACE
    }

    public data class TreeData(
        val root: TreeNode
    )

    private data class NodeHitbox(
        val node: TreeNode,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val toggleX: Float,
        val toggleY: Float,
        val toggleW: Float,
        val toggleH: Float,
        val actionX: Float,
        val actionY: Float,
        val actionW: Float,
        val actionH: Float,
        val sliderX: Float,
        val sliderW: Float
    )

    private data class BoundsF(
        val minX: Float,
        val minY: Float,
        val maxX: Float,
        val maxY: Float
    )

    private fun isExpanded(node: TreeNode): Boolean {
        return expandedById[node.id] ?: node.expanded
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        val treeData = treeDataProvider()

        hitboxes.clear()

        // Defensive: ensure we start with neutral textured GUI state.
        ensureGuiTexturedState()

        // Mouse position in tree-space (after inverse pan+scale), used for hover states.
        val treeMouseX = (context.mouseX - panX) / scale
        val treeMouseY = (context.mouseY - panY) / scale

        // Slider dragging: update continuously each frame.
        val sliderDragId = draggingSliderNodeId
        if (sliderDragId != null) {
            val hb = hitboxes.firstOrNull { it.node.id == sliderDragId }
            val node = hb?.node
            val spec = node?.slider
            if (hb != null && node != null && spec != null && hb.sliderW > 0f) {
                val v = computeSliderValue(treeMouseX, hb.sliderX, hb.sliderW, spec.min, spec.max)
                node.onSliderChanged?.invoke(v)
            }
        }

        GlStateManager.pushMatrix()
        GlStateManager.translate(panX, panY, 0f)
        GlStateManager.scale(scale, scale, 1f)

        if (treeData != null) {
            drawNode(context, widgetTheme, treeData.root, 0f, HEADER_HEIGHT.toFloat(), 0, treeMouseX, treeMouseY)
        } else {
            val mc = Minecraft.getMinecraft()
            mc.fontRenderer.drawStringWithShadow(
                "No structure data",
                4f, HEADER_HEIGHT + 4f,
                Color.GREY.main
            )
        }

        GlStateManager.popMatrix()

        // Clamp pan after we have hitboxes (content bounds), so the user can't drag the content completely away.
        if (treeData != null && hitboxes.isNotEmpty()) {
            val bounds = computeContentBounds()
            maybeInitView(treeData.root.id, bounds)
            clampPanToBounds(bounds)
        }

        // Safety: reset GL state so subsequent widgets won't inherit anything.
        ensureGuiTexturedState()

        super.draw(context, widgetTheme)
    }

    private fun drawNode(
        context: ModularGuiContext,
        widgetTheme: WidgetThemeEntry<*>,
        node: TreeNode,
        x: Float,
        y: Float,
        depth: Int,
        treeMouseX: Float,
        treeMouseY: Float
    ): Float {
        val nodeW = calculateNodeWidth(node)
        // tree_diagram.png is 32x14.
        val nodeH = TREE_H.toFloat()

        // Right-aligned controls (md-style): toggle at far right; slider (if any) sits to its left.
        val rightPad = 4f
        val hasToggle = node.children.isNotEmpty()
        val toggleW = if (hasToggle) EC_W else 0f
        val toggleH = if (hasToggle) EC_H else 0f
        val toggleX = if (hasToggle) (x + nodeW - rightPad - toggleW) else 0f
        val toggleY = if (hasToggle) (y + (nodeH - toggleH) * 0.5f) else 0f

        val sliderW = if (node.slider != null) 72f else 0f
        val sliderX = if (sliderW > 0f) {
            val rightEdge = if (hasToggle) (toggleX - 4f) else (x + nodeW - rightPad)
            (rightEdge - sliderW).coerceAtLeast(x + 4f)
        } else 0f

        // Material option action button (right side). Only used by MATERIAL_OPTION rows.
        val actionLabel = node.actionLabel
        val actionW = if (actionLabel != null) {
            val w = Minecraft.getMinecraft().fontRenderer.getStringWidth(actionLabel).toFloat() + 8f
            w.coerceAtLeast(24f)
        } else 0f
        val actionH = if (actionLabel != null) 10f else 0f
        val actionX = if (actionLabel != null) (x + nodeW - rightPad - actionW) else 0f
        val actionY = if (actionLabel != null) (y + (nodeH - actionH) * 0.5f) else 0f

        // Hitbox (in transformed tree space; input handling will inverse-transform mouse coords).
        hitboxes.add(
            NodeHitbox(
                node = node,
                x = x,
                y = y,
                w = nodeW,
                h = nodeH,
                toggleX = toggleX,
                toggleY = toggleY,
                toggleW = toggleW,
                toggleH = toggleH,
                actionX = actionX,
                actionY = actionY,
                actionW = actionW,
                actionH = actionH,
                sliderX = sliderX,
                sliderW = sliderW
            )
        )

        fun rectF(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
            // Use floor/ceil so adjacent segments always overlap by at least 1px after int conversion.
            val l = kotlin.math.floor(kotlin.math.min(x1, x2).toDouble()).toInt()
            val r = kotlin.math.ceil(kotlin.math.max(x1, x2).toDouble()).toInt()
            val t = kotlin.math.floor(kotlin.math.min(y1, y2).toDouble()).toInt()
            val b = kotlin.math.ceil(kotlin.math.max(y1, y2).toDouble()).toInt()
            if (r > l && b > t) {
                Gui.drawRect(l, t, r, b, color)
                // drawRect may change GL state; restore for subsequent textured draws.
                ensureGuiTexturedState()
            }
        }

        fun rectExact(x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
            // Like Gui.drawRect, but keeps sub-pixel coordinates.
            val l = kotlin.math.min(x1, x2).toDouble()
            val r = kotlin.math.max(x1, x2).toDouble()
            val t = kotlin.math.min(y1, y2).toDouble()
            val b = kotlin.math.max(y1, y2).toDouble()
            if (r <= l || b <= t) return

            val a = (color ushr 24 and 255) / 255f
            val rr = (color ushr 16 and 255) / 255f
            val gg = (color ushr 8 and 255) / 255f
            val bb = (color and 255) / 255f

            GlStateManager.disableTexture2D()
            GlStateManager.enableBlend()
            GlStateManager.disableAlpha()
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
            GlStateManager.color(rr, gg, bb, a)

            val tess = Tessellator.getInstance()
            val buf = tess.buffer
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION)
            buf.pos(r, t, 0.0).endVertex()
            buf.pos(l, t, 0.0).endVertex()
            buf.pos(l, b, 0.0).endVertex()
            buf.pos(r, b, 0.0).endVertex()
            tess.draw()

            GlStateManager.enableTexture2D()
            GlStateManager.enableAlpha()
            ensureGuiTexturedState()
        }

        fun connH(x1: Float, x2: Float, yMid: Float) {
            // 2px thick horizontal connector.
            rectF(x1, yMid - 1f, x2, yMid + 1f, CONN_COLOR)
            // Shadow: 1px thin edge directly below the bar (same length, no horizontal shift).
            rectF(x1, yMid + 1f, x2, yMid + 2f, CONN_SHADOW)
        }

        fun connV(xMid: Float, y1: Float, y2: Float) {
            // 2px thick vertical connector.
            rectF(xMid - 1f, y1, xMid + 1f, y2, CONN_COLOR)
            // Shadow: 1px thin edge directly to the right of the bar (same height, no vertical shift).
            rectF(xMid + 1f, y1, xMid + 2f, y2, CONN_SHADOW)
        }

        // Selected highlight: border only (no fill), per request.
        if (node.id == selectedNodeId) {
            val c = 0xFF2E6BFF.toInt()
            rectF(x, y, x + nodeW, y + 1f, c)
            rectF(x, y + nodeH - 1f, x + nodeW, y + nodeH, c)
            rectF(x, y, x + 1f, y + nodeH, c)
            rectF(x + nodeW - 1f, y, x + nodeW, y + nodeH, c)
        }

        // Base row visuals
        when (node.type) {
            NodeType.MATERIAL_PANEL -> {
                // A collapsible sub-panel header. When expanded, draw a slightly darker border.
                rectF(x, y, x + nodeW, y + nodeH, PANEL_BG)
                val border = if (isExpanded(node)) PANEL_BORDER_EXPANDED else PANEL_BORDER
                rectF(x, y, x + nodeW, y + 1f, border)
                rectF(x, y + nodeH - 1f, x + nodeW, y + nodeH, border)
                rectF(x, y, x + 1f, y + nodeH, border)
                rectF(x + nodeW - 1f, y, x + nodeW, y + nodeH, border)
            }

            NodeType.MATERIAL_OPTION -> {
                // Options sit inside the materials panel; draw subtle bg so it reads as a group.
                rectF(x, y, x + nodeW, y + nodeH, 0x12000000)
            }

            NodeType.SLICE_SLIDER -> {
                rectF(x, y, x + nodeW, y + nodeH, 0x16000000)
            }

            else -> {
                ensureGuiTexturedState()
                TREE_BG.draw(x, y, TREE_BG_W.toFloat(), nodeH)
                val midW = kotlin.math.max(0f, nodeW - TREE_BG_W.toFloat() - TREE_RIGHT_W.toFloat())
                if (midW > 0) {
                    TREE_MID.draw(x + TREE_BG_W.toFloat(), y, midW, nodeH)
                }
                TREE_RIGHT.draw(x + TREE_BG_W.toFloat() + midW, y, TREE_RIGHT_W.toFloat(), nodeH)
            }
        }

        val mc = Minecraft.getMinecraft()

        // Expand/collapse toggle visual (ec_button) - only for nodes that have children.
        // Behaviour: press -> show pressed; release (while still in toggle hitbox) -> toggle.
        if (node.children.isNotEmpty()) {
            ensureGuiTexturedState()
            val hovered = treeMouseX >= toggleX && treeMouseX <= (toggleX + EC_W) &&
                treeMouseY >= toggleY && treeMouseY <= (toggleY + EC_H)
            val pressed = pressedToggleNodeId == node.id

            val expanded = isExpanded(node)
            val tex = if (expanded) {
                when {
                    pressed -> EC_WITHDRAW_PRESSED
                    hovered -> EC_WITHDRAW_SELECTED
                    else -> EC_WITHDRAW_DEFAULT
                }
            } else {
                when {
                    pressed -> EC_EXPAND_PRESSED
                    hovered -> EC_EXPAND_SELECTED
                    else -> EC_EXPAND_DEFAULT
                }
            }
            tex.draw(toggleX, toggleY, EC_W, EC_H)
        }

        val textColor = when (node.type) {
            NodeType.MATERIAL_OPTION -> if (node.enabled) OPTION_TEXT else OPTION_TEXT_DIM
            else -> if (node.enabled) Color.WHITE.main else Color.GREY.main
        }

        // Material option icon (scaled down to fit 14px row height)
        val icon = node.iconStack
        var textX = x + 14f
        if (icon != null) {
            // Render at ~12x12.
            val ix = (x + 2f).toInt()
            val iy = (y + 1f).toInt()
            GlStateManager.pushMatrix()
            GlStateManager.translate(ix.toFloat(), iy.toFloat(), 0f)
            GlStateManager.scale(0.75f, 0.75f, 1f)
            RenderHelper.enableGUIStandardItemLighting()
            mc.renderItem.renderItemAndEffectIntoGUI(icon, 0, 0)
            RenderHelper.disableStandardItemLighting()
            GlStateManager.popMatrix()
            ensureGuiTexturedState()
            textX = x + 16f
        }

        ensureGuiTexturedState()
        mc.fontRenderer.drawStringWithShadow(node.label, textX, y + 4f, textColor)

        // Material option action button (right side)
        if (actionLabel != null && actionW > 0f && actionH > 0f) {
            val hoveredAction = treeMouseX >= actionX && treeMouseX <= (actionX + actionW) &&
                treeMouseY >= actionY && treeMouseY <= (actionY + actionH)
            val enabledAction = node.actionEnabled && node.onActivate != null

            val bg = when {
                !enabledAction -> 0x22000000
                hoveredAction -> 0x55376DFF
                else -> 0x442E6BFF
            }
            rectF(actionX, actionY, actionX + actionW, actionY + actionH, bg)
            rectF(actionX, actionY, actionX + actionW, actionY + 1f, 0x66FFFFFF)
            ensureGuiTexturedState()

            val tw = mc.fontRenderer.getStringWidth(actionLabel).toFloat()
            val tx = actionX + (actionW - tw) * 0.5f
            val ty = actionY + 1f
            val tc = if (enabledAction) 0xFFFFFFFF.toInt() else 0xFFAAAAAA.toInt()
            mc.fontRenderer.drawStringWithShadow(actionLabel, tx, ty, tc)
        }

        // Slider (for slice count) drawn on the right.
        val sliderSpec = node.slider
        if (sliderSpec != null) {
            ensureGuiTexturedState()
            val trackX = sliderX
            val trackW = sliderW
            val trackY = y + (nodeH - SLIDER_H) * 0.5f

            // Base: 3-slice stretch (rl_base_expand: L=5, M=1, R=6)
            val midW = (trackW - SLIDER_BASE_L_W - SLIDER_BASE_R_W).coerceAtLeast(0f)
            SLIDER_S_X_BASE_L.draw(trackX, trackY, SLIDER_BASE_L_W.toFloat(), SLIDER_H)
            if (midW > 0f) {
                SLIDER_S_X_BASE_M.draw(trackX + SLIDER_BASE_L_W, trackY, midW, SLIDER_H)
            }
            SLIDER_S_X_BASE_R.draw(trackX + SLIDER_BASE_L_W + midW, trackY, SLIDER_BASE_R_W.toFloat(), SLIDER_H)

            val denom = (sliderSpec.max - sliderSpec.min).coerceAtLeast(1)
            val t = (sliderSpec.value - sliderSpec.min).toFloat() / denom.toFloat()

            val innerX = trackX + 1f
            val innerW = (trackW - 2f).coerceAtLeast(SLIDER_HANDLE_W)
            val handleX = innerX + (innerW - SLIDER_HANDLE_W) * t
            val handleY = trackY + 1f

            val hoveredHandle = treeMouseX >= handleX && treeMouseX <= (handleX + SLIDER_HANDLE_W) &&
                treeMouseY >= handleY && treeMouseY <= (handleY + SLIDER_HANDLE_H)
            val pressedHandle = draggingSliderNodeId == node.id
            val handleTex = when {
                pressedHandle -> SLIDER_S_X_HANDLE_PRESSED
                hoveredHandle -> SLIDER_S_X_HANDLE_SELECTED
                else -> SLIDER_S_X_HANDLE_DEFAULT
            }
            handleTex.draw(handleX, handleY, SLIDER_HANDLE_W, SLIDER_HANDLE_H)
        }

        if (depth > 0) {
            // Left-side connector into this node (from parent's vertical rail).
            connH(x - 1f, x, y + 7f)
        }

        var currentY = y + nodeH + 2f

        if (isExpanded(node) && node.children.isNotEmpty()) {
            val railX = x + 6f
            val childX = x + 12f

            var firstChildY: Float? = null
            var lastChildY: Float? = null
            val childYs = mutableListOf<Float>()

            for (child in node.children) {
                val childY = currentY
                if (firstChildY == null) firstChildY = childY
                lastChildY = childY
                childYs += childY

                // Horizontal segment from rail to child node.
                // Start 1px earlier so it fully covers the 2px vertical rail width.
                connH(railX - 1f, childX - 1f, childY + 7f)

                currentY = drawNode(context, widgetTheme, child, childX, childY, depth + 1, treeMouseX, treeMouseY)
            }

            // Continuous vertical rail from the bottom of this node down to the last child.
            // This prevents the connector from looking "broken" between siblings.
            val lastY = lastChildY
            if (lastY != null) {
                connV(railX, y + nodeH, lastY + 7f)

                // Overlay: cover the rail's shadow at each branch junction so it looks fully connected.
                // This intentionally draws AFTER the vertical rail (which has a right/bottom shadow).
                for (cy in childYs) {
                    rectF(railX - 1f, (cy + 7f) - 1f, railX + 2f, (cy + 7f) + 1f, CONN_COLOR)
                }
            }
        }

        return currentY
    }

    private fun calculateNodeWidth(node: TreeNode): Float {
        val mc = Minecraft.getMinecraft()
        val textW = mc.fontRenderer.getStringWidth(node.label)
        val sliderW = if (node.slider != null) 72f else 0f
        val toggleW = if (node.children.isNotEmpty()) (EC_W + 6f) else 0f
        val actionW = node.actionLabel?.let { (mc.fontRenderer.getStringWidth(it).toFloat() + 12f).coerceAtLeast(28f) } ?: 0f
        val iconW = if (node.iconStack != null) 14f else 0f
        // Ensure slider has enough room.
        return (14f + iconW + textW + 20f + toggleW + sliderW + actionW).coerceAtLeast(31f)
    }

    override fun onMousePressed(mouseButton: Int): Interactable.Result {
        // Right button drag: always pan the tree view, even when clicking on interactive nodes.
        if (mouseButton == 1) {
            dragging = true
            draggingButton = 1
            dragStartX = context.mouseX
            dragStartY = context.mouseY
            dragStartPanX = panX
            dragStartPanY = panY
            return Interactable.Result.SUCCESS
        }

        if (mouseButton != 0) return Interactable.Result.IGNORE

        // NOTE: ModularUI applies this widget's transformation matrix before calling Interactable callbacks.
        // Therefore context.mouseX/mouseY are already in *this widget's local coordinate space*.
        val mx = context.mouseX
        val my = context.mouseY

        if (my in 0 until HEADER_HEIGHT) {
            dragging = true
            draggingButton = 0
            dragStartX = mx
            dragStartY = my
            dragStartPanX = panX
            dragStartPanY = panY
            return Interactable.Result.SUCCESS
        }

        // Click-to-select / expand-collapse.
        // Convert mouse position to tree-space coordinates (inverse of translate+scale).
        val treeX = (mx - panX) / scale
        val treeY = (my - panY) / scale

        // Find the first hitbox that contains this point.
        // (Nodes don't overlap; order doesn't matter much, but reverse gives "top-most" semantics.)
        val hb = hitboxes.asReversed().firstOrNull { h ->
            treeX >= h.x && treeX <= (h.x + h.w) &&
                treeY >= h.y && treeY <= (h.y + h.h)
        }

        if (hb != null) {
            val node = hb.node
            val inToggle = hb.toggleW > 0f &&
                treeX >= hb.toggleX && treeX <= (hb.toggleX + hb.toggleW) &&
                treeY >= hb.toggleY && treeY <= (hb.toggleY + hb.toggleH)

            val inAction = hb.actionW > 0f &&
                treeX >= hb.actionX && treeX <= (hb.actionX + hb.actionW) &&
                treeY >= hb.actionY && treeY <= (hb.actionY + hb.actionH)

            if (inToggle && node.children.isNotEmpty()) {
                // Pressed state is shown immediately; the toggle happens on release.
                pressedToggleNodeId = node.id
            } else if (node.slider != null && hb.sliderW > 0f &&
                treeX >= hb.sliderX && treeX <= (hb.sliderX + hb.sliderW) &&
                treeY >= hb.y && treeY <= (hb.y + hb.h)
            ) {
                // Start dragging slider; value updates continuously in draw().
                draggingSliderNodeId = node.id
                val s = node.slider!!
                val v = computeSliderValue(treeX, hb.sliderX, hb.sliderW, s.min, s.max)
                node.onSliderChanged?.invoke(v)
            } else if (node.type == NodeType.MATERIAL_OPTION) {
                // Material option UX:
                // - click row -> select (for preview highlight)
                // - click right-side action button -> activate (persist selection)
                if (inAction && node.actionEnabled && node.onActivate != null) {
                    node.onActivate.invoke()
                } else {
                    selectedNodeId = node.id
                    onSelectionChanged?.invoke(node)
                }
            } else if (node.onActivate != null) {
                node.onActivate.invoke()
            } else {
                selectedNodeId = node.id
                onSelectionChanged?.invoke(node)
            }
            return Interactable.Result.SUCCESS
        }

        // Clicked empty area: clear selection.
        if (selectedNodeId != null) {
            selectedNodeId = null
            onSelectionChanged?.invoke(null)
            return Interactable.Result.SUCCESS
        }

        return Interactable.Result.IGNORE
    }

    override fun onMouseRelease(mouseButton: Int): Boolean {
        if (mouseButton == 0) {
            draggingSliderNodeId = null
            // Toggle on release if we released over the same toggle hitbox.
            val pressedId = pressedToggleNodeId
            if (pressedId != null) {
                // Convert mouse position to tree-space.
                val treeX = (context.mouseX - panX) / scale
                val treeY = (context.mouseY - panY) / scale
                val hb = hitboxes.firstOrNull { it.node.id == pressedId }
                if (hb != null) {
                    val inToggle = hb.toggleW > 0f &&
                        treeX >= hb.toggleX && treeX <= (hb.toggleX + hb.toggleW) &&
                        treeY >= hb.toggleY && treeY <= (hb.toggleY + hb.toggleH)
                    if (inToggle && hb.node.children.isNotEmpty()) {
                        val now = isExpanded(hb.node)
                        expandedById[hb.node.id] = !now
                    }
                }
                pressedToggleNodeId = null
            }
        }
        if (mouseButton == draggingButton) {
            dragging = false
            draggingButton = -1
        }
        return true
    }

    private fun computeSliderValue(treeX: Float, sliderX: Float, sliderW: Float, min: Int, max: Int): Int {
        val denom = (max - min).coerceAtLeast(1)
        val innerX = sliderX + 1f
        val innerW = (sliderW - 2f).coerceAtLeast(SLIDER_HANDLE_W)
        val t = ((treeX - innerX) / (innerW - SLIDER_HANDLE_W)).let { raw ->
            if ((innerW - SLIDER_HANDLE_W) <= 0.0001f) 0f else raw
        }.coerceIn(0f, 1f)
        return (min + kotlin.math.round(t * denom).toInt()).coerceIn(min, max)
    }

    override fun onMouseDrag(mouseButton: Int, timeSinceClick: Long) {
        if (dragging && mouseButton == draggingButton) {
            val dx = context.mouseX - dragStartX
            val dy = context.mouseY - dragStartY
            panX = dragStartPanX + dx
            panY = dragStartPanY + dy
            // Clamp continuously while dragging.
            if (hitboxes.isNotEmpty()) {
                clampPanToBounds(computeContentBounds())
            }
        }
    }

    override fun onMouseScroll(direction: UpOrDown, amount: Int): Boolean {
        val delta = if (direction == UpOrDown.UP) -1f else 1f
        val ctrl = Interactable.hasControlDown()
        val shift = Interactable.hasShiftDown()

        when {
            ctrl -> {
                scale = (scale - delta * ZOOM_STEP).coerceIn(MIN_SCALE, MAX_SCALE)
            }
            shift -> {
                panX -= delta * SCROLL_SPEED
            }
            else -> {
                panY -= delta * SCROLL_SPEED
            }
        }

        if (hitboxes.isNotEmpty()) {
            clampPanToBounds(computeContentBounds())
        }

        return true
    }

    public fun resetView() {
        panX = 0f
        panY = 0f
        scale = 1f
        viewInitializedForRootId = null
    }

    private fun computeContentBounds(): BoundsF {
        // Expand bounds slightly to account for connector rails and shadows.
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (h in hitboxes) {
            minX = kotlin.math.min(minX, h.x - 4f)
            minY = kotlin.math.min(minY, h.y - 2f)
            maxX = kotlin.math.max(maxX, h.x + h.w + 4f)
            maxY = kotlin.math.max(maxY, h.y + h.h + 2f)
        }
        if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) {
            return BoundsF(0f, HEADER_HEIGHT.toFloat(), 0f, HEADER_HEIGHT.toFloat())
        }
        return BoundsF(minX, minY, maxX, maxY)
    }

    private fun maybeInitView(rootId: String, bounds: BoundsF) {
        if (viewInitializedForRootId == rootId) return
        viewInitializedForRootId = rootId

        // Start with a predictable origin: keep the root near top-left (below header) with a small margin.
        val marginX = 2f
        val marginTop = (HEADER_HEIGHT + 2).toFloat()
        panX = marginX - bounds.minX * scale
        panY = marginTop - bounds.minY * scale
    }

    private fun clampPanToBounds(bounds: BoundsF) {
        val viewW = area.width.toFloat()
        val viewH = area.height.toFloat()
        if (viewW <= 0f || viewH <= 0f) return

        val topMargin = (HEADER_HEIGHT + 2).toFloat()
        val bottomMargin = 4f
        val sideMargin = 4f

        val contentW = (bounds.maxX - bounds.minX) * scale
        val contentH = (bounds.maxY - bounds.minY) * scale

        // X clamp
        val minPanX = if (contentW + sideMargin * 2 <= viewW) {
            // Content fits: center it.
            (viewW - contentW) * 0.5f - bounds.minX * scale
        } else {
            // Content wider: allow scrolling but keep some content visible.
            (viewW - sideMargin) - bounds.maxX * scale
        }
        val maxPanX = if (contentW + sideMargin * 2 <= viewW) {
            minPanX
        } else {
            sideMargin - bounds.minX * scale
        }
        panX = panX.coerceIn(minPanX, maxPanX)

        // Y clamp (keep below header)
        val minPanY = if (contentH + topMargin + bottomMargin <= viewH) {
            // Fits: align to top margin.
            topMargin - bounds.minY * scale
        } else {
            (viewH - bottomMargin) - bounds.maxY * scale
        }
        val maxPanY = if (contentH + topMargin + bottomMargin <= viewH) {
            minPanY
        } else {
            topMargin - bounds.minY * scale
        }
        panY = panY.coerceIn(minPanY, maxPanY)
    }
}
