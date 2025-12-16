package github.kasuminova.prototypemachinery.client.preview.ui.widget

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.widget.IGuiAction
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import org.lwjgl.opengl.GL11
import org.lwjgl.util.glu.GLU
import kotlin.math.abs
import kotlin.math.max

/**
 * 3D structure preview widget.
 *
 * Design goals:
 * - Host-agnostic & JEI-friendly: does NOT require a world.
 * - Low coupling: rendering is contained inside this widget.
 * - Interactive: drag to rotate, scroll to zoom.
 *
 * Rendering strategy (initial milestone):
 * - Draw boundary voxels as colored wireframe cubes.
 * - Optional per-block status coloring via [statusProvider].
 */
internal class StructurePreview3DWidget(
    private val model: StructurePreviewModel,
    private val statusProvider: (() -> Map<BlockPos, StructurePreviewEntryStatus>)? = null,
    /** When true, only render cubes whose status is not MATCH. */
    private val issuesOnlyProvider: (() -> Boolean)? = null,
    /** When true, only render cubes at [sliceYProvider] (relative Y within bounds). */
    private val sliceModeProvider: (() -> Boolean)? = null,
    /** Relative Y (0..sizeY-1) used when [sliceModeProvider] is true. */
    private val sliceYProvider: (() -> Int)? = null,
    /** When true, slowly rotates the camera automatically. */
    private val autoRotateProvider: (() -> Boolean)? = null
) : Widget<StructurePreview3DWidget>() {

    private data class Cube(val x: Int, val y: Int, val z: Int, val keyHash: Int)

    private val cubes: List<Cube>
    private val min: BlockPos = model.bounds.min
    private val maxB: BlockPos = model.bounds.max

    private var yawDeg: Float = 35f
    private var pitchDeg: Float = 25f
    private var zoom: Float = 1.0f

    private var dragging: Boolean = false
    private var dragButton: Int = -1
    private var lastDragAbsX: Int = 0
    private var lastDragAbsY: Int = 0

    init {
        cubes = buildBoundaryCubes(model)
    }

    fun resetView() {
        yawDeg = 35f
        pitchDeg = 25f
        zoom = 1.0f
        dragging = false
        dragButton = -1
    }

    override fun onUpdate() {
        super.onUpdate()
        if (autoRotateProvider?.invoke() == true) {
            yawDeg += 2.0f
        }
    }

    override fun afterInit() {
        super.afterInit()

        // Mouse press: start dragging when clicked inside widget.
        listenGuiAction(object : IGuiAction.MousePressed {
            override fun press(mouseButton: Int): Boolean {
                val ctx = context
                if (!ctx.isMouseAbove(this@StructurePreview3DWidget)) return false
                dragging = true
                dragButton = mouseButton
                lastDragAbsX = ctx.absMouseX
                lastDragAbsY = ctx.absMouseY
                return true
            }
        })

        // Mouse release: stop drag.
        listenGuiAction(object : IGuiAction.MouseReleased {
            override fun release(mouseButton: Int): Boolean {
                if (!dragging) return false
                if (mouseButton != dragButton) return false
                dragging = false
                dragButton = -1
                return true
            }
        })

        // Mouse drag: rotate camera.
        listenGuiAction(object : IGuiAction.MouseDrag {
            override fun drag(mouseButton: Int, timeSinceClick: Long): Boolean {
                if (!dragging) return false
                if (mouseButton != dragButton) return false

                val ctx = context
                val dx = ctx.absMouseX - lastDragAbsX
                val dy = ctx.absMouseY - lastDragAbsY

                // Avoid huge jumps on lost focus.
                if (abs(dx) > 200 || abs(dy) > 200) {
                    lastDragAbsX = ctx.absMouseX
                    lastDragAbsY = ctx.absMouseY
                    return true
                }

                yawDeg += dx * 0.65f
                pitchDeg += dy * 0.65f
                if (pitchDeg < -89f) pitchDeg = -89f
                if (pitchDeg > 89f) pitchDeg = 89f

                lastDragAbsX = ctx.absMouseX
                lastDragAbsY = ctx.absMouseY
                return true
            }
        })

        // Scroll: zoom.
        listenGuiAction(object : IGuiAction.MouseScroll {
            override fun scroll(direction: UpOrDown, amount: Int): Boolean {
                val ctx = context
                if (!ctx.isMouseAbove(this@StructurePreview3DWidget)) return false

                val step = 1.0f + (amount.coerceAtMost(10) * 0.08f)
                zoom = when (direction) {
                    UpOrDown.UP -> (zoom / step)
                    UpOrDown.DOWN -> (zoom * step)
                }
                if (zoom < 0.25f) zoom = 0.25f
                if (zoom > 8.0f) zoom = 8.0f
                return true
            }
        })
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        // Avoid any heavy work if not visible.
        if (!isEnabled || !areAncestorsEnabled()) return
        if (area.w() <= 2 || area.h() <= 2) return

        val mc = Minecraft.getMinecraft()
        val scaled = ScaledResolution(mc)
        val scale = scaled.scaleFactor

        val scX = area.x() * scale
        val scY = mc.displayHeight - (area.y() + area.h()) * scale
        val scW = area.w() * scale
        val scH = area.h() * scale

        GlStateManager.pushMatrix()
        GlStateManager.pushAttrib()

        // Clip to widget area.
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(scX, scY, scW, scH)

        // Setup 3D.
        GlStateManager.disableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.enableDepth()
        GlStateManager.depthFunc(GL11.GL_LEQUAL)
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)

        // Clear depth only (do not clear color of whole screen!).
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)

        // Projection matrix.
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        val aspect = area.w().toFloat() / area.h().toFloat()
        GLU.gluPerspective(45f, aspect, 0.1f, 2000f)

        // ModelView matrix.
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()

        val sizeX = (maxB.x - min.x + 1).coerceAtLeast(1)
        val sizeY = (maxB.y - min.y + 1).coerceAtLeast(1)
        val sizeZ = (maxB.z - min.z + 1).coerceAtLeast(1)
        val maxDim = max(sizeX, max(sizeY, sizeZ)).toFloat()

        // Place camera.
        val baseDistance = maxDim * 2.6f
        val cameraDist = baseDistance * zoom
        GlStateManager.translate(0f, 0f, -cameraDist)
        GlStateManager.rotate(pitchDeg, 1f, 0f, 0f)
        GlStateManager.rotate(yawDeg, 0f, 1f, 0f)

        // Center structure around origin.
        val cx = sizeX * 0.5f
        val cy = sizeY * 0.5f
        val cz = sizeZ * 0.5f
        GlStateManager.translate(-cx, -cy, -cz)

        // Draw axes (subtle).
        drawAxes(sizeX.toFloat(), sizeY.toFloat(), sizeZ.toFloat())

        // Draw cubes.
        drawCubes(context)

        // Restore matrices.
        GlStateManager.popMatrix() // MODELVIEW
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.popMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GlStateManager.popAttrib()
        GlStateManager.popMatrix()

        // Let base class handle hover timers etc.
        super.draw(context, widgetTheme)
    }

    private fun drawAxes(x: Float, y: Float, z: Float) {
        val t = Tessellator.getInstance()
        val b = t.buffer
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)

        // X (red)
        b.pos(0.0, 0.0, 0.0).color(255, 80, 80, 120).endVertex()
        b.pos(x.toDouble(), 0.0, 0.0).color(255, 80, 80, 120).endVertex()

        // Y (green)
        b.pos(0.0, 0.0, 0.0).color(80, 255, 80, 120).endVertex()
        b.pos(0.0, y.toDouble(), 0.0).color(80, 255, 80, 120).endVertex()

        // Z (blue)
        b.pos(0.0, 0.0, 0.0).color(80, 80, 255, 120).endVertex()
        b.pos(0.0, 0.0, z.toDouble()).color(80, 80, 255, 120).endVertex()

        t.draw()
    }

    private fun drawCubes(context: ModularGuiContext) {
        val statuses = statusProvider?.invoke() ?: emptyMap()

        val issuesOnly = issuesOnlyProvider?.invoke() == true
        val sliceMode = sliceModeProvider?.invoke() == true
        val sizeY = (maxB.y - min.y + 1).coerceAtLeast(1)
        val sliceY = (sliceYProvider?.invoke() ?: 0).coerceIn(0, sizeY - 1)

        val t = Tessellator.getInstance()
        val b = t.buffer
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)

        for (c in cubes) {
            if (sliceMode && c.y != sliceY) continue
            val rel = BlockPos(c.x + min.x, c.y + min.y, c.z + min.z)
            val status = statuses[rel]
            if (issuesOnly && status == StructurePreviewEntryStatus.MATCH) continue
            val rgba = colorFor(status, c.keyHash)
            val r = (rgba shr 16) and 0xFF
            val g = (rgba shr 8) and 0xFF
            val bl = (rgba) and 0xFF
            val a = (rgba ushr 24) and 0xFF

            addWireCube(b, c.x.toDouble(), c.y.toDouble(), c.z.toDouble(), r, g, bl, a)
        }

        t.draw()

        // Slightly thicker outline is not available with tessellator easily; leave it thin for now.
    }

    private fun colorFor(status: StructurePreviewEntryStatus?, keyHash: Int): Int {
        return when (status) {
            StructurePreviewEntryStatus.MATCH -> 0xCC55FF55.toInt()
            StructurePreviewEntryStatus.MISSING -> 0xCCFF5555.toInt()
            StructurePreviewEntryStatus.WRONG -> 0xCCFFAA55.toInt()
            StructurePreviewEntryStatus.UNLOADED -> 0xCC55FFFF.toInt()
            StructurePreviewEntryStatus.UNKNOWN, null -> {
                // Stable pseudo-color by key hash (avoid everything looking the same in JEI mode).
                val h = keyHash
                val r = 120 + (h and 0x3F)
                val g = 120 + ((h shr 6) and 0x3F)
                val b = 120 + ((h shr 12) and 0x3F)
                (0xAA shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun addWireCube(
        b: net.minecraft.client.renderer.BufferBuilder,
        x: Double,
        y: Double,
        z: Double,
        r: Int,
        g: Int,
        bl: Int,
        a: Int
    ) {
        val x0 = x
        val y0 = y
        val z0 = z
        val x1 = x + 1.0
        val y1 = y + 1.0
        val z1 = z + 1.0

        // 12 edges.
        line(b, x0, y0, z0, x1, y0, z0, r, g, bl, a)
        line(b, x0, y1, z0, x1, y1, z0, r, g, bl, a)
        line(b, x0, y0, z1, x1, y0, z1, r, g, bl, a)
        line(b, x0, y1, z1, x1, y1, z1, r, g, bl, a)

        line(b, x0, y0, z0, x0, y1, z0, r, g, bl, a)
        line(b, x1, y0, z0, x1, y1, z0, r, g, bl, a)
        line(b, x0, y0, z1, x0, y1, z1, r, g, bl, a)
        line(b, x1, y0, z1, x1, y1, z1, r, g, bl, a)

        line(b, x0, y0, z0, x0, y0, z1, r, g, bl, a)
        line(b, x1, y0, z0, x1, y0, z1, r, g, bl, a)
        line(b, x0, y1, z0, x0, y1, z1, r, g, bl, a)
        line(b, x1, y1, z0, x1, y1, z1, r, g, bl, a)
    }

    private fun line(
        b: net.minecraft.client.renderer.BufferBuilder,
        x0: Double,
        y0: Double,
        z0: Double,
        x1: Double,
        y1: Double,
        z1: Double,
        r: Int,
        g: Int,
        bl: Int,
        a: Int
    ) {
        b.pos(x0, y0, z0).color(r, g, bl, a).endVertex()
        b.pos(x1, y1, z1).color(r, g, bl, a).endVertex()
    }

    private fun buildBoundaryCubes(model: StructurePreviewModel): List<Cube> {
        val blocks = model.blocks
        if (blocks.isEmpty()) return emptyList()

        val present = HashSet<BlockPos>(blocks.size * 2)
        for (p in blocks.keys) present.add(p)

        val out = ArrayList<Cube>(blocks.size)
        for ((pos, req) in blocks) {
            if (!isBoundary(pos, present)) continue
            out.add(
                Cube(
                    x = pos.x - min.x,
                    y = pos.y - min.y,
                    z = pos.z - min.z,
                    keyHash = req.stableKey().hashCode()
                )
            )
        }
        return out
    }

    private fun isBoundary(pos: BlockPos, present: Set<BlockPos>): Boolean {
        // If any neighbor is absent, this cube contributes to the surface.
        for (f in EnumFacing.values()) {
            if (!present.contains(pos.offset(f))) return true
        }
        return false
    }
}
