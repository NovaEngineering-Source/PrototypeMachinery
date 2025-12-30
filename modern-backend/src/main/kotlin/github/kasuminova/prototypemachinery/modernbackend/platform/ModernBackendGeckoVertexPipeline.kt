package github.kasuminova.prototypemachinery.modernbackend.platform

import github.kasuminova.prototypemachinery.api.platform.PMGeckoVertexPipeline
import github.kasuminova.prototypemachinery.modernbackend.accel.BatchedVertexPipeline

internal class ModernBackendGeckoVertexPipeline : PMGeckoVertexPipeline {

    private val selected: BatchedVertexPipeline = BatchedVertexPipeline.selected()
    private val scalar: BatchedVertexPipeline = BatchedVertexPipeline.scalar()

    override fun backendName(forceScalar: Boolean): String {
        return (if (forceScalar) scalar else selected).backendName()
    }

    override fun isVectorized(forceScalar: Boolean): Boolean {
        return (if (forceScalar) scalar else selected).isVectorized()
    }

    override fun transformThenPackIntArray(
        forceScalar: Boolean,
        xs: FloatArray,
        ys: FloatArray,
        zs: FloatArray,
        us: FloatArray,
        vs: FloatArray,
        colors: IntArray,
        normals: IntArray,
        vertexOffset: Int,
        count: Int,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float,
        out: IntArray,
        outIntOffset: Int
    ): Boolean {
        return try {
            val p = if (forceScalar) scalar else selected
            p.transformThenPackIntArray(
                xs, ys, zs,
                us, vs,
                colors, normals,
                vertexOffset, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23,
                out, outIntOffset
            )
            true
        } catch (_: Throwable) {
            false
        }
    }

    override fun transformAffine3x4InPlace(
        forceScalar: Boolean,
        xs: FloatArray,
        ys: FloatArray,
        zs: FloatArray,
        vertexOffset: Int,
        count: Int,
        m00: Float,
        m01: Float,
        m02: Float,
        m03: Float,
        m10: Float,
        m11: Float,
        m12: Float,
        m13: Float,
        m20: Float,
        m21: Float,
        m22: Float,
        m23: Float
    ): Boolean {
        return try {
            val p = if (forceScalar) scalar else selected
            p.transformAffine3x4InPlace(
                xs, ys, zs,
                vertexOffset, count,
                m00, m01, m02, m03,
                m10, m11, m12, m13,
                m20, m21, m22, m23
            )
            true
        } catch (_: Throwable) {
            false
        }
    }
}
