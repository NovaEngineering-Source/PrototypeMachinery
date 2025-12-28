package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.RenderFrameClock
import github.kasuminova.prototypemachinery.client.impl.render.assets.CachingAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.ExternalDiskTextureBinder
import github.kasuminova.prototypemachinery.client.impl.render.assets.MinecraftAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.MountedDirectoryAssetResolver
import github.kasuminova.prototypemachinery.client.impl.render.assets.ResolverBackedResourceManager
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderBuildTask
import github.kasuminova.prototypemachinery.client.util.BufferBuilderPool
import github.kasuminova.prototypemachinery.client.util.MmceMatrixStack
import github.kasuminova.prototypemachinery.common.util.OrientationMath
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import software.bernie.geckolib3.file.GeoModelLoader
import java.nio.file.Path

internal data class GeckoRenderSnapshot(
    internal val ownerKey: Any,
    internal val renderKey: RenderKey,
    internal val pass: RenderPass,

    internal val geoLocation: ResourceLocation,
    internal val textureLocation: ResourceLocation,

    internal val animationLocation: ResourceLocation?,
    internal val animationNames: List<String> = emptyList(),

    internal val bakeMode: GeckoModelBaker.BakeMode = GeckoModelBaker.BakeMode.ALL,

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
 */
internal class GeckoModelRenderBuildTask(
    private val snapshot: GeckoRenderSnapshot,
) : RenderBuildTask(snapshot.ownerKey) {

    override fun currentKey(): RenderKey = snapshot.renderKey

    override fun build(key: RenderKey): BuiltBuffers {
        // Kick off external texture prefetch early (non-blocking). This avoids any disk I/O on the render thread.
        ExternalDiskTextureBinder.prefetch(snapshot.textureLocation)

        val resolver = CachingAssetResolver(
            MountedDirectoryAssetResolver(
                delegate = MinecraftAssetResolver,
                rootDir = snapshot.resourcesRoot,
                namespaces = setOf(snapshot.geoLocation.namespace),
            )
        )
        val manager = ResolverBackedResourceManager(resolver, domains = setOf(snapshot.geoLocation.namespace))

        val stamp = resolver.versionStamp()

        // IMPORTANT:
        // GeoModel (bones) is mutable: do NOT share one instance across different ownerKey.
        // For dynamic tasks (ANIMATED_ONLY) we can safely reuse per-owner to avoid re-loading/parsing JSON every tick.
        val model = if (snapshot.bakeMode == GeckoModelBaker.BakeMode.ANIMATED_ONLY) {
            GeckoGeoModelInstanceCache.getOrLoad(snapshot.ownerKey, snapshot.geoLocation, stamp, manager)
        } else {
            GeoModelLoader().loadModel(manager, snapshot.geoLocation)
        }

        // Tick-rate animation evaluation (do NOT do per-frame to avoid cache churn).
        val animationLocation = snapshot.animationLocation
        val animationKey = if (key.animationTimeKey != 0) key.animationTimeKey else key.animationStateHash
        if (animationLocation != null && animationKey != 0 && snapshot.bakeMode == GeckoModelBaker.BakeMode.ANIMATED_ONLY) {
            GeckoAnimationDriver.apply(
                ownerKey = snapshot.ownerKey,
                geoModel = model,
                animationLocation = animationLocation,
                animationNames = snapshot.animationNames,
                resourceManager = manager,
                seekTimeTicks = if (key.animationTimeKey != 0) {
                    RenderFrameClock.seekTimeTicksFromKey(key.animationTimeKey)
                } else {
                    key.animationStateHash.toDouble()
                },
            )
        }

        // If caller forces a pass, bake everything into that single pass.
        // Otherwise, route by bone name (MMCE-style): bloom/emissive/transparent prefixes.
        val forcedPass = snapshot.pass.takeIf { it != RenderPass.DEFAULT }

        val buildersByPass = linkedMapOf<RenderPass, BufferBuilder>()

        fun getOrCreate(pass: RenderPass): BufferBuilder {
            return buildersByPass.getOrPut(pass) {
                BufferBuilderPool.borrow(32 * 1024, tag = "GeckoModelRenderBuildTask.$pass").also {
                    it.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX_COLOR_NORMAL)
                }
            }
        }

        val ms = MmceMatrixStack()
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

        val boneIndex = if (animationLocation != null) {
            GeckoAnimatedBoneIndex.getIndex(
                resourceManager = manager,
                resourcesRoot = snapshot.resourcesRoot.toString(),
                animationLocation = animationLocation,
            )
        } else {
            null
        }

        val activeAnimatedBones = if (boneIndex != null) {
            // IMPORTANT:
            // We treat a bone as "animated" only if it is referenced by the *currently selected* animations.
            // This enables a "temporary static" layer: bones not touched by the active animation(s) will be
            // baked into the static task and no longer rebuilt every tick.
            boneIndex.animatedBonesFor(snapshot.animationNames)
        } else {
            emptySet()
        }

        val potentialAnimatedBones = boneIndex?.allAnimatedBones ?: emptySet()

        GeckoModelBaker.bakeRoutedFiltered(
            model = model,
            matrixStack = ms,
            activeAnimatedBones = activeAnimatedBones,
            mode = snapshot.bakeMode,
            potentialAnimatedBones = potentialAnimatedBones,
            bufferSelector = { bloom, transparent ->
                val pass = forcedPass ?: when {
                    bloom && transparent -> RenderPass.BLOOM_TRANSPARENT
                    bloom -> RenderPass.BLOOM
                    transparent -> RenderPass.TRANSPARENT
                    else -> RenderPass.DEFAULT
                }
                getOrCreate(pass)
            },
        )

        ms.pop()

        // Finalize all buffers we touched.
        buildersByPass.values.forEach { it.finishDrawing() }

        return BuiltBuffers(byPass = buildersByPass)
    }


    private fun rotateOrientation(ms: MmceMatrixStack, front: EnumFacing, top: EnumFacing) {
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

    private fun rotateXCentered(ms: MmceMatrixStack, degrees: Double) {
        // Rotate around the block center (y=0.5) instead of the block bottom.
        // This avoids pivot-related offsets for UP/DOWN and mixed 24-orientation sequences.
        ms.translate(0.0f, 0.5f, 0.0f)
        ms.rotateX(Math.toRadians(degrees).toFloat())
        ms.translate(0.0f, -0.5f, 0.0f)
    }
}
