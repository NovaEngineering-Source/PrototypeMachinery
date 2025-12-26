package github.kasuminova.prototypemachinery.client.model

import github.kasuminova.prototypemachinery.common.block.MachineBlock
import net.minecraft.block.state.IBlockState
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.block.model.IBakedModel
import net.minecraft.client.renderer.block.model.ItemOverrideList
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.model.TRSRTransformation
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import javax.vecmath.AxisAngle4f
import javax.vecmath.Matrix4f
import javax.vecmath.Vector3f

/**
 * A baked model wrapper that applies twist rotation around the FACING axis.
 *
 * Standard blockstate only supports X and Y rotations, but twist requires rotation
 * around the FACING axis (which could be Z for north/south facing).
 *
 * This wrapper intercepts getQuads() and applies the appropriate rotation transform
 * to all quads based on the (facing, twist) combination in the blockstate.
 */
@SideOnly(Side.CLIENT)
internal class TwistRotatedBakedModel(
    private val baseModel: IBakedModel,
) : IBakedModel by baseModel {

    // Cache for transformed quads: key = (facing.ordinal * 4 + twist) * 7 + (cullFace?.ordinal ?: 6)
    private val quadCache: Array<List<BakedQuad>?> = arrayOfNulls(6 * 4 * 7)

    override fun getQuads(state: IBlockState?, side: EnumFacing?, rand: Long): List<BakedQuad> {
        if (state == null) {
            return baseModel.getQuads(null, side, rand)
        }

        val facing = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
        val twist = runCatching { state.getValue(MachineBlock.TWIST) }.getOrDefault(0)

        if (twist == 0) {
            // No twist, just apply facing rotation using standard logic
            return getBaseFacingQuads(facing, side, rand)
        }

        val cacheKey = (facing.ordinal * 4 + twist) * 7 + (side?.ordinal ?: 6)
        quadCache[cacheKey]?.let { return it }

        val baseQuads = getBaseFacingQuads(facing, null, rand) +
            EnumFacing.entries.flatMap { getBaseFacingQuads(facing, it, rand) }

        val transform = getTwistTransform(facing, twist)
        val transformed = baseQuads.map { transformQuad(it, transform, side) }.filter { it != null }.map { it!! }

        // Filter by cull face
        val result = if (side == null) {
            transformed.filter { it.face == null }
        } else {
            transformed.filter { it.face == side }
        }

        quadCache[cacheKey] = result
        return result
    }

    private fun getBaseFacingQuads(facing: EnumFacing, side: EnumFacing?, rand: Long): List<BakedQuad> {
        // Create a temporary state with facing and twist=0 to get base rotated quads
        // We rely on the underlying model having correct facing rotation
        return baseModel.getQuads(null, side, rand)
    }

    private fun getTwistTransform(facing: EnumFacing, twist: Int): TRSRTransformation {
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

        val matrix = Matrix4f()
        matrix.setIdentity()

        // Translate to center, rotate, translate back
        matrix.setTranslation(Vector3f(0.5f, 0.5f, 0.5f))

        val rotation = Matrix4f()
        rotation.setIdentity()
        rotation.setRotation(AxisAngle4f(axis, angle))

        val temp = Matrix4f()
        temp.setIdentity()
        temp.setTranslation(Vector3f(-0.5f, -0.5f, -0.5f))

        matrix.mul(rotation)
        matrix.mul(temp)

        return TRSRTransformation(matrix)
    }

    private fun transformQuad(quad: BakedQuad, transform: TRSRTransformation, targetSide: EnumFacing?): BakedQuad? {
        val vertexData = quad.vertexData.clone()
        val format = DefaultVertexFormats.ITEM

        val stride = format.size / 4 // integers per vertex

        for (v in 0 until 4) {
            val offset = v * stride

            // Extract position
            val x = java.lang.Float.intBitsToFloat(vertexData[offset])
            val y = java.lang.Float.intBitsToFloat(vertexData[offset + 1])
            val z = java.lang.Float.intBitsToFloat(vertexData[offset + 2])

            // Transform position
            val pos = javax.vecmath.Vector4f(x, y, z, 1f)
            transform.matrix.transform(pos)

            vertexData[offset] = java.lang.Float.floatToRawIntBits(pos.x)
            vertexData[offset + 1] = java.lang.Float.floatToRawIntBits(pos.y)
            vertexData[offset + 2] = java.lang.Float.floatToRawIntBits(pos.z)

            // Transform normal if present
            val normalOffset = offset + 6 // Position(3) + Color(1) + UV(2)
            if (normalOffset < vertexData.size) {
                val packedNormal = vertexData[normalOffset]
                val nx = ((packedNormal shr 0) and 0xFF).toByte().toFloat() / 127f
                val ny = ((packedNormal shr 8) and 0xFF).toByte().toFloat() / 127f
                val nz = ((packedNormal shr 16) and 0xFF).toByte().toFloat() / 127f

                val normal = javax.vecmath.Vector3f(nx, ny, nz)
                transform.matrix.transform(normal)
                normal.normalize()

                val newNormal = ((((normal.x * 127f).toInt() and 0xFF) shl 0) or
                    (((normal.y * 127f).toInt() and 0xFF) shl 8) or
                    (((normal.z * 127f).toInt() and 0xFF) shl 16))
                vertexData[normalOffset] = newNormal
            }
        }

        // Transform cull face
        val newFace = if (quad.face != null) {
            transformFacing(quad.face, transform)
        } else {
            EnumFacing.UP // Default face for quads without cull face
        }

        return BakedQuad(vertexData, quad.tintIndex, newFace, quad.sprite)
    }

    private fun transformFacing(facing: EnumFacing, transform: TRSRTransformation): EnumFacing {
        val normal = javax.vecmath.Vector3f(
            facing.xOffset.toFloat(),
            facing.yOffset.toFloat(),
            facing.zOffset.toFloat()
        )
        transform.matrix.transform(normal)
        return EnumFacing.getFacingFromVector(normal.x, normal.y, normal.z)
    }

    override fun isAmbientOcclusion(): Boolean = baseModel.isAmbientOcclusion

    override fun isGui3d(): Boolean = baseModel.isGui3d

    override fun isBuiltInRenderer(): Boolean = baseModel.isBuiltInRenderer

    override fun getParticleTexture(): TextureAtlasSprite = baseModel.particleTexture

    override fun getOverrides(): ItemOverrideList = baseModel.overrides
}
