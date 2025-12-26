package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.assets.MinecraftAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.MountedDirectoryAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.ResolverBackedResourceManager
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderBuildTask
import github.kasuminova.prototypemachinery.common.util.OrientationMath
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import software.bernie.geckolib3.file.GeoModelLoader
import software.bernie.geckolib3.util.MatrixStack
import java.nio.file.Path

internal data class GeckoRenderSnapshot(
    internal val ownerKey: Any,
    internal val renderKey: RenderKey,
    internal val pass: RenderPass,

    internal val geoLocation: ResourceLocation,
    internal val textureLocation: ResourceLocation,

    internal val x: Double,
    internal val y: Double,
    internal val z: Double,

    /**
     * Additional translation in structure/model local units (blocks).
     *
     * This offset is applied *after* orientation rotation, so it rotates with (front/top).
     */
    internal val modelOffsetX: Double,
    internal val modelOffsetY: Double,
    internal val modelOffsetZ: Double,

    /**
     * Structure/block orientation.
     *
     * - front: where the controller faces (MachineBlock.FACING)
     * - top: the controller's top direction (full 24-orientation support; must not be parallel to front)
     */
    internal val front: EnumFacing,
    internal val top: EnumFacing,

    internal val resourcesRoot: Path,
    internal val yOffset: Double,
)

/**
 * Background task that bakes a GeckoLib geo model into a BufferBuilder.
 *
 * MVP: static pose only (no animation evaluation yet).
 */
internal class GeckoModelRenderBuildTask(
    private val snapshot: GeckoRenderSnapshot,
) : RenderBuildTask(snapshot.ownerKey) {

    override fun currentKey(): RenderKey = snapshot.renderKey

    override fun build(key: RenderKey): BuiltBuffers {
        val resolver = MountedDirectoryAssetResolver(
            delegate = MinecraftAssetResolver,
            rootDir = snapshot.resourcesRoot,
            namespaces = setOf(snapshot.geoLocation.namespace),
        )
        val manager = ResolverBackedResourceManager(resolver, domains = setOf(snapshot.geoLocation.namespace))

        val model = GeoModelLoader().loadModel(manager, snapshot.geoLocation)

        // If caller forces a pass, bake everything into that single pass.
        // Otherwise, route by bone name (MMCE-style): bloom/emissive/transparent prefixes.
        val forcedPass = snapshot.pass.takeIf { it != RenderPass.DEFAULT }

        val buildersByPass = linkedMapOf<RenderPass, BufferBuilder>()

        fun getOrCreate(pass: RenderPass): BufferBuilder {
            return buildersByPass.getOrPut(pass) {
                BufferBuilder(32 * 1024).also {
                    it.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL)
                }
            }
        }

        val ms = MatrixStack()
        ms.push()

        // World-space placement (RenderManager already translates to camera origin).
        ms.translate(
            snapshot.x.toFloat(),
            (snapshot.y + snapshot.yOffset).toFloat(),
            snapshot.z.toFloat()
        )
        ms.translate(0.0f, 0.01f, 0.0f)
        ms.translate(0.5f, 0.0f, 0.5f)

        rotateOrientation(ms, snapshot.front, snapshot.top)

        // Local offsets that should rotate with the structure orientation.
        // (Applied after rotateOrientation to be affected by the rotation matrix.)
        ms.translate(
            snapshot.modelOffsetX.toFloat(),
            snapshot.modelOffsetY.toFloat(),
            snapshot.modelOffsetZ.toFloat()
        )

        if (forcedPass != null) {
            val builder = getOrCreate(forcedPass)
            GeckoModelBaker.bake(model, builder, ms)
        } else {
            GeckoModelBaker.bakeRouted(
                model = model,
                matrixStack = ms,
                bufferSelector = { bloom, transparent ->
                    val pass = when {
                        bloom && transparent -> RenderPass.BLOOM_TRANSPARENT
                        bloom -> RenderPass.BLOOM
                        transparent -> RenderPass.TRANSPARENT
                        else -> RenderPass.DEFAULT
                    }
                    getOrCreate(pass)
                }
            )
        }

        ms.pop()

        // Finalize all buffers we touched.
        buildersByPass.values.forEach { it.finishDrawing() }

        return BuiltBuffers(byPass = buildersByPass)
    }


    private fun rotateOrientation(ms: MatrixStack, front: EnumFacing, top: EnumFacing) {
        val steps = OrientationMath.stepsFromBase(front, top)
        for (step in steps) {
            when (step) {
                OrientationMath.RotationStep.Y_POS -> ms.rotateY(Math.toRadians(90.0).toFloat())
                OrientationMath.RotationStep.Y_NEG -> ms.rotateY(Math.toRadians(-90.0).toFloat())
                OrientationMath.RotationStep.X_POS -> rotateXCentered(ms, 90.0)
                OrientationMath.RotationStep.X_NEG -> rotateXCentered(ms, -90.0)
            }
        }
    }

    private fun rotateXCentered(ms: MatrixStack, degrees: Double) {
        // Rotate around the block center (y=0.5) instead of the block bottom.
        // This avoids pivot-related offsets for UP/DOWN and mixed 24-orientation sequences.
        ms.translate(0.0f, 0.5f, 0.0f)
        ms.rotateX(Math.toRadians(degrees).toFloat())
        ms.translate(0.0f, -0.5f, 0.0f)
    }
}
