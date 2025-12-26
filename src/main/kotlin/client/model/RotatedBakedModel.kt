package github.kasuminova.prototypemachinery.client.model

import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import javax.vecmath.AxisAngle4f
import javax.vecmath.Matrix4f
import javax.vecmath.Vector3f
import javax.vecmath.Vector4f

/**
 * A baked model that applies twist rotation around the facing axis.
 *
 * All quads are pre-transformed at construction time for efficiency.
 */
@SideOnly(Side.CLIENT)
internal class RotatedBakedModel(
    private val baseModel: IBakedModel,
    private val facing: EnumFacing,
    private val twist: Int,
) : IBakedModel {

    // Pre-computed rotated quads for each cull face (null = no culling)
    private val rotatedQuads: Map<EnumFacing?, List<BakedQuad>>

    init {
        val transform = createTwistTransform(facing, twist)
        rotatedQuads = computeRotatedQuads(baseModel, transform)
    }

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        return rotatedQuads[side] ?: emptyList()
    }

    private fun createTwistTransform(facing: EnumFacing, twist: Int): Matrix4f {
        if (twist == 0) {
            return Matrix4f().apply { setIdentity() }
        }

        val angle = (twist * 90.0f) * (Math.PI.toFloat() / 180.0f)

        // Rotation axis is the facing direction
        val axis = when (facing) {
            EnumFacing.NORTH -> Vector3f(0f, 0f, -1f)
            EnumFacing.SOUTH -> Vector3f(0f, 0f, 1f)
            EnumFacing.EAST -> Vector3f(1f, 0f, 0f)
            EnumFacing.WEST -> Vector3f(-1f, 0f, 0f)
            EnumFacing.UP -> Vector3f(0f, 1f, 0f)
            EnumFacing.DOWN -> Vector3f(0f, -1f, 0f)
        }

        // Compose: translate to center -> rotate -> translate back
        val result = Matrix4f()
        result.setIdentity()

        // T(0.5, 0.5, 0.5)
        val t1 = Matrix4f()
        t1.setIdentity()
        t1.setTranslation(Vector3f(0.5f, 0.5f, 0.5f))

        // R(axis, angle)
        val r = Matrix4f()
        r.setIdentity()
        r.setRotation(AxisAngle4f(axis, angle))

        // T(-0.5, -0.5, -0.5)
        val t2 = Matrix4f()
        t2.setIdentity()
        t2.setTranslation(Vector3f(-0.5f, -0.5f, -0.5f))

        result.mul(t1)
        result.mul(r)
        result.mul(t2)

        return result
    }

    private fun computeRotatedQuads(model: IBakedModel, transform: Matrix4f): Map<EnumFacing?, List<BakedQuad>> {
        val result = mutableMapOf<EnumFacing?, MutableList<BakedQuad>>()

        // Initialize lists for all possible cull faces
        result[null] = mutableListOf()
        EnumFacing.entries.forEach { result[it] = mutableListOf() }

        // Process quads from all cull faces
        val allCullFaces = listOf(null) + EnumFacing.entries
        for (cullFace in allCullFaces) {
            val quads = model.getQuads(null, cullFace, 0)
            for (quad in quads) {
                val transformed = transformQuad(quad, transform)
                val newCullFace = transformed.first
                result[newCullFace]!!.add(transformed.second)
            }
        }

        return result
    }

    private fun transformQuad(quad: BakedQuad, transform: Matrix4f): Pair<EnumFacing?, BakedQuad> {
        val vertexData = quad.vertexData.clone()
        val format = DefaultVertexFormats.BLOCK
        val stride = format.size / 4 // integers per vertex (should be 7 for BLOCK format)

        for (v in 0 until 4) {
            val offset = v * stride

            // Extract position (first 3 floats)
            val x = java.lang.Float.intBitsToFloat(vertexData[offset])
            val y = java.lang.Float.intBitsToFloat(vertexData[offset + 1])
            val z = java.lang.Float.intBitsToFloat(vertexData[offset + 2])

            // Transform position
            val pos = Vector4f(x, y, z, 1f)
            transform.transform(pos)

            vertexData[offset] = java.lang.Float.floatToRawIntBits(pos.x)
            vertexData[offset + 1] = java.lang.Float.floatToRawIntBits(pos.y)
            vertexData[offset + 2] = java.lang.Float.floatToRawIntBits(pos.z)
        }

        // Transform cull face
        val newFace = if (quad.face != null) {
            transformFacing(quad.face, transform)
        } else {
            null
        }

        val newQuad = BakedQuad(
            vertexData,
            quad.tintIndex,
            transformFacing(quad.face ?: EnumFacing.UP, transform), // quad face for lighting
            quad.sprite
        )

        return newFace to newQuad
    }

    private fun transformFacing(facing: EnumFacing, transform: Matrix4f): EnumFacing {
        val normal = Vector3f(
            facing.xOffset.toFloat(),
            facing.yOffset.toFloat(),
            facing.zOffset.toFloat()
        )

        // Extract rotation part of the matrix
        val rotation = Matrix4f(transform)
        rotation.setTranslation(Vector3f(0f, 0f, 0f))
        rotation.transform(normal)
        normal.normalize()

        return EnumFacing.getFacingFromVector(normal.x, normal.y, normal.z)
    }

    override fun isAmbientOcclusion(): Boolean = baseModel.isAmbientOcclusion

    override fun isGui3d(): Boolean = baseModel.isGui3d

    override fun isBuiltInRenderer(): Boolean = baseModel.isBuiltInRenderer

    override fun getParticleTexture(): TextureAtlasSprite = baseModel.particleTexture

    override fun getOverrides(): ItemOverrideList = baseModel.overrides
}
