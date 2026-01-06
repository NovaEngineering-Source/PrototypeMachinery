package github.kasuminova.prototypemachinery.client.impl.render

import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher
import kotlin.math.floor

/**
 * Computes a per-frame world-space origin used to keep baked vertex coordinates small.
 *
 * Goal:
 * - Keep coordinates near zero (avoid float precision loss at large world coords)
 * - Be global for the frame (avoid per-origin draw overhead)
 * - Change infrequently (avoid excessive cache churn)
 */
internal object RenderWorldOrigin {

    data class Origin(val x: Int, val y: Int, val z: Int)

    /**
     * Compute a snapped origin around the camera.
     *
     * We snap to a coarse grid derived from render distance, so the origin only changes
     * when the camera crosses a grid boundary.
     */
    fun compute(renderDistanceChunks: Int, cameraX: Double, cameraY: Double, cameraZ: Double): Origin {
        val rd = renderDistanceChunks.coerceAtLeast(2)

        // Grid size in blocks.
        // - Larger grid => less frequent origin changes (less cache churn)
        // - Still small enough to keep all visible machines within a few thousand blocks of origin
        val grid = maxOf(256, rd * 16 * 2)

        val bx = floor(cameraX).toLong()
        val by = floor(cameraY).toLong()
        val bz = floor(cameraZ).toLong()

        fun snap(v: Long): Int {
            val g = grid.toLong()
            // floorDiv handles negatives correctly
            val snapped = Math.floorDiv(v, g) * g
            return snapped.toInt()
        }

        // Y is usually less problematic, but keeping it snapped avoids extreme deltas in tall structures.
        return Origin(
            x = snap(bx),
            y = snap(by),
            z = snap(bz),
        )
    }

    /**
     * Current frame origin based on the renderer's camera position.
     */
    fun current(): Origin {
        val mc = Minecraft.getMinecraft()
        val rd = mc.gameSettings.renderDistanceChunks
        return compute(
            renderDistanceChunks = rd,
            cameraX = TileEntityRendererDispatcher.staticPlayerX,
            cameraY = TileEntityRendererDispatcher.staticPlayerY,
            cameraZ = TileEntityRendererDispatcher.staticPlayerZ,
        )
    }
}
