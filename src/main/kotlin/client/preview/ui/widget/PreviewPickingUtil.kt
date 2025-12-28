package github.kasuminova.prototypemachinery.client.preview.ui.widget

import net.minecraft.client.Minecraft
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.util.glu.GLU
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Utilities for screen-space picking (GUI mouse -> display pixels -> unproject -> ray/AABB tests).
 *
 * Kept in a separate file to reduce the size/complexity of the 3D widget.
 */
internal object PreviewPickingUtil {

    internal data class GlMatricesSnapshot(
        val modelview: java.nio.FloatBuffer,
        val projection: java.nio.FloatBuffer,
        val viewport: java.nio.IntBuffer
    )

    internal data class Ray3f(
        val ox: Float,
        val oy: Float,
        val oz: Float,
        val dx: Float,
        val dy: Float,
        val dz: Float
    )

    fun toDisplayPixelMouse(mc: Minecraft, guiScale: Int, absMouseX: Int, absMouseY: Int): Pair<Int, Int> {
        // Convert GUI mouse coords (top-left origin) -> display pixel coords (bottom-left origin).
        // Note the -1: Minecraft's GUI mouseY=0 corresponds to displayY=displayHeight-1.
        val mx = absMouseX * guiScale
        val my = mc.displayHeight - absMouseY * guiScale - 1
        return mx to my
    }

    fun isInsideViewport(mx: Int, my: Int, viewportX: Int, viewportY: Int, viewportW: Int, viewportH: Int): Boolean {
        if (mx < viewportX || mx >= viewportX + viewportW) return false
        if (my < viewportY || my >= viewportY + viewportH) return false
        return true
    }

    fun readCurrentMatrices(viewportX: Int, viewportY: Int, viewportW: Int, viewportH: Int): GlMatricesSnapshot {
        val modelview = BufferUtils.createFloatBuffer(16)
        val projection = BufferUtils.createFloatBuffer(16)
        val viewport = BufferUtils.createIntBuffer(16)

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, modelview)
        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, projection)
        viewport.put(0, viewportX)
        viewport.put(1, viewportY)
        viewport.put(2, viewportW)
        viewport.put(3, viewportH)

        return GlMatricesSnapshot(modelview, projection, viewport)
    }

    fun computePickRay(mx: Int, my: Int, matrices: GlMatricesSnapshot): Ray3f? {
        val near = BufferUtils.createFloatBuffer(3)
        val far = BufferUtils.createFloatBuffer(3)

        val okNear = GLU.gluUnProject(
            mx.toFloat(),
            my.toFloat(),
            0f,
            matrices.modelview,
            matrices.projection,
            matrices.viewport,
            near
        )
        val okFar = GLU.gluUnProject(
            mx.toFloat(),
            my.toFloat(),
            1f,
            matrices.modelview,
            matrices.projection,
            matrices.viewport,
            far
        )
        if (!okNear || !okFar) return null

        val ox = near.get(0)
        val oy = near.get(1)
        val oz = near.get(2)

        var dx = far.get(0) - ox
        var dy = far.get(1) - oy
        var dz = far.get(2) - oz
        val len = kotlin.math.sqrt((dx * dx + dy * dy + dz * dz).toDouble()).toFloat()
        if (len <= 1e-6f) return null
        dx /= len
        dy /= len
        dz /= len

        return Ray3f(ox, oy, oz, dx, dy, dz)
    }

    fun rayAabbHit(
        ox: Float,
        oy: Float,
        oz: Float,
        dx: Float,
        dy: Float,
        dz: Float,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float
    ): Float? {
        var tMin = 0f
        var tMax = 1e9f

        fun slab(o: Float, d: Float, mn: Float, mx: Float): Boolean {
            if (abs(d) < 1e-6f) {
                return o in mn..mx
            }
            val inv = 1f / d
            var t0 = (mn - o) * inv
            var t1 = (mx - o) * inv
            if (t0 > t1) {
                val tmp = t0
                t0 = t1
                t1 = tmp
            }
            tMin = max(tMin, t0)
            tMax = min(tMax, t1)
            return tMax >= tMin
        }

        if (!slab(ox, dx, minX, maxX)) return null
        if (!slab(oy, dy, minY, maxY)) return null
        if (!slab(oz, dz, minZ, maxZ)) return null
        if (tMax < 0f) return null
        return if (tMin >= 0f) tMin else tMax
    }
}
