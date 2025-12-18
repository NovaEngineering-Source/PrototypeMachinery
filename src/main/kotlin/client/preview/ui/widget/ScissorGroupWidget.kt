package github.kasuminova.prototypemachinery.client.preview.ui.widget

import com.cleanroommc.modularui.api.layout.IViewport
import com.cleanroommc.modularui.api.layout.IViewportStack
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.widgets.layout.Column
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import org.lwjgl.opengl.GL11

/**
 * A lightweight container widget that clips (scissors) its children to its own [area].
 *
 * Useful for "slide in/out" animations where moved parts should be cropped.
 */
internal class ScissorGroupWidget : Column(), IViewport {

    private companion object {
        /**
         * Avoid per-widget glGetInteger(GL_SCISSOR_BOX) calls which can stall the driver.
         * We keep a simple JVM-side stack for scissor nesting and only query GL once when
         * entering from depth==0 and an external scissor is already enabled.
         */
        private var depth: Int = 0

        private var baseCaptured: Boolean = false
        private var baseEnabled: Boolean = false
        private val baseBox = IntArray(4)

        private val baseTmpBuf = org.lwjgl.BufferUtils.createIntBuffer(16)

        // Stores scissor rects as x,y,w,h packed consecutively.
        private var stack = IntArray(4 * 32)

        private fun ensureCapacity(nextDepth: Int) {
            val needed = nextDepth * 4
            if (needed <= stack.size) return
            var newSize = stack.size
            while (newSize < needed) newSize *= 2
            val grown = IntArray(newSize)
            System.arraycopy(stack, 0, grown, 0, stack.size)
            stack = grown
        }

        private fun pushRect(x: Int, y: Int, w: Int, h: Int) {
            ensureCapacity(depth + 1)
            val i = depth * 4
            stack[i + 0] = x
            stack[i + 1] = y
            stack[i + 2] = w
            stack[i + 3] = h
        }

        private fun peekRect(out: IntArray) {
            val i = (depth - 1) * 4
            out[0] = stack[i + 0]
            out[1] = stack[i + 1]
            out[2] = stack[i + 2]
            out[3] = stack[i + 3]
        }
    }

    private val tmpParent = IntArray(4)

    override fun canBeSeen(stack: IViewportStack): Boolean {
        // Important: ModularUI's default canBeSeen uses the Stencil stack for culling.
        // If this widget gets culled, IViewport#preDraw/postDraw won't run, but children may still render,
        // which would make clipping appear "not working" (notably at y=0/top edge).
        // For a clip container it's safer to always run.
        return true
    }

    /**
     * Called twice by ModularUI: once before children-viewport transform (transformed=false)
     * and once after it (transformed=true).
     *
     * We enable scissor in the *first* call so the clip rect stays fixed even if children are translated.
     */
    override fun preDraw(context: ModularGuiContext, transformed: Boolean) {
        if (transformed) return
        if (!isEnabled || !areAncestorsEnabled()) return
        if (area.w() <= 0 || area.h() <= 0) return

        val mc = Minecraft.getMinecraft()
        val scale = ScaledResolution(mc).scaleFactor

        // Capture external scissor state only once when entering from depth==0.
        if (depth == 0) {
            baseEnabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
            if (baseEnabled) {
                // LWJGL1 requires a buffer with at least 16 ints.
                baseTmpBuf.clear()
                GL11.glGetInteger(GL11.GL_SCISSOR_BOX, baseTmpBuf)
                baseBox[0] = baseTmpBuf.get(0)
                baseBox[1] = baseTmpBuf.get(1)
                baseBox[2] = baseTmpBuf.get(2)
                baseBox[3] = baseTmpBuf.get(3)
            }
            baseCaptured = true
        }

        // Compute this widget's clip rect in *screen* pixels.
        // Use the current viewport stack transform to get the widget origin without relying on Area's absolute bookkeeping.
        val xGui = context.transformX(0f, 0f)
        val yGui = context.transformY(0f, 0f)
        val wGui = area.w()
        val hGui = area.h()

        var scX = xGui * scale
        var scY = mc.displayHeight - (yGui + hGui) * scale
        var scW = wGui * scale
        var scH = hGui * scale

        // Intersect with parent scissor (nested clipping).
        val hasParent = if (depth > 0) {
            peekRect(tmpParent)
            true
        } else {
            baseEnabled
        }

        if (hasParent) {
            val ax0 = if (depth > 0) tmpParent[0] else baseBox[0]
            val ay0 = if (depth > 0) tmpParent[1] else baseBox[1]
            val aw = if (depth > 0) tmpParent[2] else baseBox[2]
            val ah = if (depth > 0) tmpParent[3] else baseBox[3]

            val ax1 = ax0 + aw
            val ay1 = ay0 + ah

            val bx0 = scX
            val by0 = scY
            val bx1 = scX + scW
            val by1 = scY + scH

            val ix0 = maxOf(ax0, bx0)
            val iy0 = maxOf(ay0, by0)
            val ix1 = minOf(ax1, bx1)
            val iy1 = minOf(ay1, by1)

            scX = ix0
            scY = iy0
            scW = maxOf(0, ix1 - ix0)
            scH = maxOf(0, iy1 - iy0)
        }

        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(scX, scY, scW, scH)

        pushRect(scX, scY, scW, scH)
        depth += 1
    }

    override fun postDraw(context: ModularGuiContext, transformed: Boolean) {
        if (transformed) return

        if (depth > 0) depth -= 1
        if (depth > 0) {
            peekRect(tmpParent)
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(tmpParent[0], tmpParent[1], tmpParent[2], tmpParent[3])
            return
        }

        // Restore base state.
        if (baseCaptured && baseEnabled) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST)
            GL11.glScissor(baseBox[0], baseBox[1], baseBox[2], baseBox[3])
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST)
        }
        baseCaptured = false
    }
}
