package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.client.util.MatrixStack
import software.bernie.geckolib3.geo.render.built.GeoCube

/**
 * Math helpers for Gecko baking/rendering.
 *
 * Intentionally kept allocation-free: callers should pass scratch/out objects.
 */
internal object GeckoBakerMath {
    internal fun floatToColorByte(v: Float): Int {
        val clamped = when {
            v <= 0.0f -> 0.0f
            v >= 1.0f -> 1.0f
            else -> v
        }
        return (clamped * 255.0f + 0.5f).toInt()
    }

    internal fun packColor(red: Float, green: Float, blue: Float, alpha: Float): Int {
        // Match vanilla RGBA packing used by BufferBuilder.
        val rI = floatToColorByte(red)
        val gI = floatToColorByte(green)
        val bI = floatToColorByte(blue)
        val aI = floatToColorByte(alpha)
        return (aI shl 24) or (bI shl 16) or (gI shl 8) or rI
    }

    internal fun floatToNormalByte(v: Float): Int {
        val clamped = when {
            v <= -1.0f -> -1.0f
            v >= 1.0f -> 1.0f
            else -> v
        }
        // Using +0.5f / -0.5f for symmetric rounding around 0.
        val scaled = clamped * 127.0f
        return if (scaled >= 0.0f) (scaled + 0.5f).toInt() else (scaled - 0.5f).toInt()
    }

    internal fun packNormal(nx: Float, ny: Float, nz: Float): Int {
        // Match BufferBuilder.normal: scale [-1,1] to signed byte [-127,127] (clamped), then pack.
        val x = floatToNormalByte(nx)
        val y = floatToNormalByte(ny)
        val z = floatToNormalByte(nz)
        return (x and 0xFF) or ((y and 0xFF) shl 8) or ((z and 0xFF) shl 16)
    }

    internal fun flatFlagsForCube(cube: GeoCube): Int {
        // Gecko flat-cube shading fix flags (mirrors GeckoLib behavior).
        val s = cube.size
        var flags = 0
        if (s.y == 0f || s.z == 0f) flags = flags or 1
        if (s.x == 0f || s.z == 0f) flags = flags or 2
        if (s.x == 0f || s.y == 0f) flags = flags or 4
        return flags
    }
}

/**
 * model matrix (3x4 affine) + normal matrix (3x3) used by the baker.
 */
internal class GeckoAffineMatrices {
    // model matrix (3x4 affine)
    var m00 = 0.0f
    var m01 = 0.0f
    var m02 = 0.0f
    var m03 = 0.0f
    var m10 = 0.0f
    var m11 = 0.0f
    var m12 = 0.0f
    var m13 = 0.0f
    var m20 = 0.0f
    var m21 = 0.0f
    var m22 = 0.0f
    var m23 = 0.0f

    // normal matrix (3x3)
    var n00 = 0.0f
    var n01 = 0.0f
    var n02 = 0.0f
    var n10 = 0.0f
    var n11 = 0.0f
    var n12 = 0.0f
    var n20 = 0.0f
    var n21 = 0.0f
    var n22 = 0.0f

    fun loadFrom(ms: MatrixStack) {
        val mm = ms.modelMatrix
        m00 = mm.m00
        m01 = mm.m01
        m02 = mm.m02
        m03 = mm.m03
        m10 = mm.m10
        m11 = mm.m11
        m12 = mm.m12
        m13 = mm.m13
        m20 = mm.m20
        m21 = mm.m21
        m22 = mm.m22
        m23 = mm.m23

        val nn = ms.normalMatrix
        n00 = nn.m00
        n01 = nn.m01
        n02 = nn.m02
        n10 = nn.m10
        n11 = nn.m11
        n12 = nn.m12
        n20 = nn.m20
        n21 = nn.m21
        n22 = nn.m22
    }

    fun setMul(bone: GeckoAffineMatrices, local: GeckoCubeLocalMatrices) {
        // model (3x4): out = bone * local
        m00 = bone.m00 * local.m00 + bone.m01 * local.m10 + bone.m02 * local.m20
        m01 = bone.m00 * local.m01 + bone.m01 * local.m11 + bone.m02 * local.m21
        m02 = bone.m00 * local.m02 + bone.m01 * local.m12 + bone.m02 * local.m22
        m03 = bone.m00 * local.m03 + bone.m01 * local.m13 + bone.m02 * local.m23 + bone.m03

        m10 = bone.m10 * local.m00 + bone.m11 * local.m10 + bone.m12 * local.m20
        m11 = bone.m10 * local.m01 + bone.m11 * local.m11 + bone.m12 * local.m21
        m12 = bone.m10 * local.m02 + bone.m11 * local.m12 + bone.m12 * local.m22
        m13 = bone.m10 * local.m03 + bone.m11 * local.m13 + bone.m12 * local.m23 + bone.m13

        m20 = bone.m20 * local.m00 + bone.m21 * local.m10 + bone.m22 * local.m20
        m21 = bone.m20 * local.m01 + bone.m21 * local.m11 + bone.m22 * local.m21
        m22 = bone.m20 * local.m02 + bone.m21 * local.m12 + bone.m22 * local.m22
        m23 = bone.m20 * local.m03 + bone.m21 * local.m13 + bone.m22 * local.m23 + bone.m23

        // normal (3x3): out = bone * local
        n00 = bone.n00 * local.n00 + bone.n01 * local.n10 + bone.n02 * local.n20
        n01 = bone.n00 * local.n01 + bone.n01 * local.n11 + bone.n02 * local.n21
        n02 = bone.n00 * local.n02 + bone.n01 * local.n12 + bone.n02 * local.n22

        n10 = bone.n10 * local.n00 + bone.n11 * local.n10 + bone.n12 * local.n20
        n11 = bone.n10 * local.n01 + bone.n11 * local.n11 + bone.n12 * local.n21
        n12 = bone.n10 * local.n02 + bone.n11 * local.n12 + bone.n12 * local.n22

        n20 = bone.n20 * local.n00 + bone.n21 * local.n10 + bone.n22 * local.n20
        n21 = bone.n20 * local.n01 + bone.n21 * local.n11 + bone.n22 * local.n21
        n22 = bone.n20 * local.n02 + bone.n21 * local.n12 + bone.n22 * local.n22
    }
}

internal class GeckoCubeLocalMatrices(
    val m00: Float,
    val m01: Float,
    val m02: Float,
    val m03: Float,
    val m10: Float,
    val m11: Float,
    val m12: Float,
    val m13: Float,
    val m20: Float,
    val m21: Float,
    val m22: Float,
    val m23: Float,
    val n00: Float,
    val n01: Float,
    val n02: Float,
    val n10: Float,
    val n11: Float,
    val n12: Float,
    val n20: Float,
    val n21: Float,
    val n22: Float,
)

/**
 * Cache for per-cube local (pivot+rotation) matrices.
 */
internal object GeckoCubeLocalMatrixCache {
    // GeoCubes are owned by Gecko model instances; use weak keys to avoid pinning models forever.
    private val cache: java.util.concurrent.ConcurrentMap<GeoCube, GeckoCubeLocalMatrices> =
        com.google.common.collect.MapMaker().weakKeys().makeMap()

    fun get(cube: GeoCube): GeckoCubeLocalMatrices {
        val hit = cache[cube]
        if (hit != null) return hit

        val computed = compute(cube)
        val prev = cache.putIfAbsent(cube, computed)
        return prev ?: computed
    }

    private fun compute(cube: GeoCube): GeckoCubeLocalMatrices {
        val ms = MatrixStack()
        ms.moveToPivot(cube)
        ms.rotate(cube)
        ms.moveBackFromPivot(cube)

        val mm = ms.modelMatrix
        val nn = ms.normalMatrix
        return GeckoCubeLocalMatrices(
            m00 = mm.m00,
            m01 = mm.m01,
            m02 = mm.m02,
            m03 = mm.m03,
            m10 = mm.m10,
            m11 = mm.m11,
            m12 = mm.m12,
            m13 = mm.m13,
            m20 = mm.m20,
            m21 = mm.m21,
            m22 = mm.m22,
            m23 = mm.m23,
            n00 = nn.m00,
            n01 = nn.m01,
            n02 = nn.m02,
            n10 = nn.m10,
            n11 = nn.m11,
            n12 = nn.m12,
            n20 = nn.m20,
            n21 = nn.m21,
            n22 = nn.m22,
        )
    }
}
