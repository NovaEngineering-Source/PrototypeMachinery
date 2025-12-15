package github.kasuminova.prototypemachinery.client.preview

import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Ray marching (DDA) for raycasting into structure preview.
 *
 * 用 DDA 算法进行射线步进以查询结构投影方块。
 */
internal object ProjectionRayMarcher {

    /**
     * Ray-march in voxel grid (Amanatides & Woo) to find the nearest preview block even when looking at air.
     * 体素 DDA 步进，用于在看向空气时也能找到最近的预览方块。
     */
    fun findPreviewBlockAlongRay(
        anchor: BlockPos,
        model: StructurePreviewModel,
        start: Vec3d,
        dir: Vec3d,
        maxDist: Double
    ): BlockPos? {
        // Avoid NaNs / zero direction
        val dx = dir.x
        val dy = dir.y
        val dz = dir.z
        if (dx == 0.0 && dy == 0.0 && dz == 0.0) return null

        var x = floor(start.x).toInt()
        var y = floor(start.y).toInt()
        var z = floor(start.z).toInt()

        val stepX = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val stepY = if (dy > 0) 1 else if (dy < 0) -1 else 0
        val stepZ = if (dz > 0) 1 else if (dz < 0) -1 else 0

        fun intBound(s: Double, ds: Double): Double {
            // distance to next integer boundary along ray
            if (ds > 0) return (ceil(s) - s) / ds
            if (ds < 0) return (s - floor(s)) / -ds
            return Double.POSITIVE_INFINITY
        }

        val tDeltaX = if (dx != 0.0) abs(1.0 / dx) else Double.POSITIVE_INFINITY
        val tDeltaY = if (dy != 0.0) abs(1.0 / dy) else Double.POSITIVE_INFINITY
        val tDeltaZ = if (dz != 0.0) abs(1.0 / dz) else Double.POSITIVE_INFINITY

        var tMaxX = intBound(start.x, dx)
        var tMaxY = intBound(start.y, dy)
        var tMaxZ = intBound(start.z, dz)

        var steps = 0

        while (steps < ProjectionConfig.RAY_MARCH_MAX_STEPS) {
            val rel = BlockPos(x - anchor.x, y - anchor.y, z - anchor.z)
            if (model.blocks.containsKey(rel)) {
                return BlockPos(x, y, z)
            }

            val tNext = minOf(tMaxX, tMaxY, tMaxZ)
            if (tNext > maxDist) return null

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                x += stepX
                tMaxX += tDeltaX
            } else if (tMaxY <= tMaxX && tMaxY <= tMaxZ) {
                y += stepY
                tMaxY += tDeltaY
            } else {
                z += stepZ
                tMaxZ += tDeltaZ
            }

            steps++
        }

        return null
    }
}
