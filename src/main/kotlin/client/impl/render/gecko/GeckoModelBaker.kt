package github.kasuminova.prototypemachinery.client.impl.render.gecko

import github.kasuminova.prototypemachinery.client.util.MmceMatrixStack
import net.minecraft.client.renderer.BufferBuilder
import software.bernie.geckolib3.geo.render.built.GeoBone
import software.bernie.geckolib3.geo.render.built.GeoCube
import software.bernie.geckolib3.geo.render.built.GeoModel
import software.bernie.geckolib3.geo.render.built.GeoQuad
import software.bernie.geckolib3.geo.render.built.GeoVertex
import javax.vecmath.Vector3f

/**
 * CPU-side baker that converts GeckoLib built models into a [BufferBuilder].
 *
 * This is adapted from GeckoLib's `IGeoRenderer` but:
 * - avoids Tessellator/GlStateManager
 * - uses a per-bake [MmceMatrixStack] (thread-safe)
 */
internal object GeckoModelBaker {

    internal enum class BakeMode {
        /** Bake all bones. */
        ALL,

        /**
         * Bake only bones that are *never* affected by any animation.
         *
         * A bone is considered "potentially animated" if it is referenced by *any* animation in the file
         * (or is under such a bone).
         */
        PERMANENT_STATIC_ONLY,

        /**
         * Bake only bones that are potentially animated (appear in some animation in the file),
         * but are not affected by the currently selected animation(s).
         *
         * This is the "temporary static" layer used to reduce animation-switch rebuild cost.
         */
        TEMP_STATIC_ONLY,

        /** Bake only bones that are affected by animation (animated bone or has animated ancestor). */
        ANIMATED_ONLY,
    }

    internal fun bake(
        model: GeoModel,
        builder: BufferBuilder,
        matrixStack: MmceMatrixStack,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        val scratch = Scratch()
        for (bone in model.topLevelBones) {
            renderRecursively(builder, matrixStack, bone, red, green, blue, alpha, scratch)
        }
    }

    /**
     * Bake a model while routing geometry into different buffers based on bone name.
     *
     * This follows MMCE's convention:
     * - bloom/emissive: bone name starts with "bloom" or "emissive" -> bloom flag
     * - transparent: bone name starts with "transparent" or "emissive_transparent" or "bloom_transparent" -> transparent flag
     *
     * The caller decides how (bloom, transparent) maps to an actual [BufferBuilder].
     */
    internal fun bakeRouted(
        model: GeoModel,
        matrixStack: MmceMatrixStack,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        val scratch = Scratch()
        for (bone in model.topLevelBones) {
            renderRecursivelyRouted(
                ms = matrixStack,
                bone = bone,
                bufferSelector = bufferSelector,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                bloom = false,
                transparent = false,
                scratch = scratch,
            )
        }
    }

    /**
     * Bake a model while filtering bones based on animation influence.
     *
     * This is used to split a model into two independent buffers:
     * - static: geometry not affected by animation
     * - dynamic: geometry affected by animation
     */
    internal fun bakeRoutedFiltered(
        model: GeoModel,
        matrixStack: MmceMatrixStack,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        potentialAnimatedBones: Set<String> = activeAnimatedBones,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        if (mode == BakeMode.ALL) {
            bakeRouted(
                model = model,
                matrixStack = matrixStack,
                bufferSelector = bufferSelector,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
            )
            return
        }

        for (bone in model.topLevelBones) {
            renderRecursivelyRoutedFiltered(
                ms = matrixStack,
                bone = bone,
                bufferSelector = bufferSelector,
                potentialAnimatedBones = potentialAnimatedBones,
                activeAnimatedBones = activeAnimatedBones,
                mode = mode,
                red = red,
                green = green,
                blue = blue,
                alpha = alpha,
                bloom = false,
                transparent = false,
                parentPotential = false,
                parentDynamic = false,
                scratch = Scratch(),
            )
        }
    }

    private class Scratch(
        val normal: Vector3f = Vector3f(),
    )

    private fun renderRecursively(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        bone: GeoBone,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ) {
        ms.push()

        ms.translate(bone)
        ms.moveToPivot(bone)
        ms.rotate(bone)
        ms.scale(bone)
        ms.moveBackFromPivot(bone)

        if (!bone.isHidden) {
            for (cube in bone.childCubes) {
                ms.push()
                renderCube(builder, ms, cube, red, green, blue, alpha, scratch)
                ms.pop()
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            for (child in bone.childBones) {
                renderRecursively(builder, ms, child, red, green, blue, alpha, scratch)
            }
        }

        ms.pop()
    }

    private fun renderRecursivelyRouted(
        ms: MmceMatrixStack,
        bone: GeoBone,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        bloom: Boolean,
        transparent: Boolean,
        scratch: Scratch,
    ) {
        // MMCE-compatible naming conventions
        var bloomNow = bloom
        var transparentNow = transparent
        val name = bone.name
        if (name.startsWith("emissive") || name.startsWith("bloom")) {
            bloomNow = true
        }
        if (
            name.startsWith("transparent") ||
            name.startsWith("emissive_transparent") ||
            name.startsWith("bloom_transparent")
        ) {
            transparentNow = true
        }

        ms.push()

        ms.translate(bone)
        ms.moveToPivot(bone)
        ms.rotate(bone)
        ms.scale(bone)
        ms.moveBackFromPivot(bone)

        if (!bone.isHidden) {
            for (cube in bone.childCubes) {
                ms.push()
                val builder = bufferSelector(bloomNow, transparentNow)
                renderCube(builder, ms, cube, red, green, blue, alpha, scratch)
                ms.pop()
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            for (child in bone.childBones) {
                renderRecursivelyRouted(
                    ms = ms,
                    bone = child,
                    bufferSelector = bufferSelector,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    bloom = bloomNow,
                    transparent = transparentNow,
                    scratch = scratch,
                )
            }
        }

        ms.pop()
    }

    private fun renderRecursivelyRoutedFiltered(
        ms: MmceMatrixStack,
        bone: GeoBone,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        potentialAnimatedBones: Set<String>,
        activeAnimatedBones: Set<String>,
        mode: BakeMode,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        bloom: Boolean,
        transparent: Boolean,
        parentPotential: Boolean,
        parentDynamic: Boolean,
        scratch: Scratch,
    ) {
        val selfPotential = potentialAnimatedBones.contains(bone.name)
        val selfDynamic = activeAnimatedBones.contains(bone.name)

        val inPotentialSubtree = parentPotential || selfPotential
        val inDynamicSubtree = parentDynamic || selfDynamic

        when (mode) {
            BakeMode.PERMANENT_STATIC_ONLY -> {
                // Any potentially-animated bone makes the whole subtree non-permanent.
                if (inPotentialSubtree) return
            }

            BakeMode.TEMP_STATIC_ONLY -> {
                // We only care about bones under potentially animated roots.
                if (!inPotentialSubtree) return
                // If a bone is under a currently animated root, the whole subtree is dynamic.
                if (inDynamicSubtree) return
            }

            BakeMode.ANIMATED_ONLY -> {
                // We still need to traverse to reach potentially animated descendants.
                // Rendering is gated further below.
            }

            BakeMode.ALL -> Unit
        }

        // MMCE-compatible naming conventions
        var bloomNow = bloom
        var transparentNow = transparent
        val name = bone.name
        if (name.startsWith("emissive") || name.startsWith("bloom")) {
            bloomNow = true
        }
        if (
            name.startsWith("transparent") ||
            name.startsWith("emissive_transparent") ||
            name.startsWith("bloom_transparent")
        ) {
            transparentNow = true
        }

        ms.push()

        ms.translate(bone)
        ms.moveToPivot(bone)
        ms.rotate(bone)
        ms.scale(bone)
        ms.moveBackFromPivot(bone)

        val shouldRenderBone = when (mode) {
            BakeMode.PERMANENT_STATIC_ONLY -> true
            BakeMode.TEMP_STATIC_ONLY -> true
            BakeMode.ANIMATED_ONLY -> inDynamicSubtree
            BakeMode.ALL -> true
        }

        if (shouldRenderBone && !bone.isHidden) {
            for (cube in bone.childCubes) {
                ms.push()
                val builder = bufferSelector(bloomNow, transparentNow)
                renderCube(builder, ms, cube, red, green, blue, alpha, scratch)
                ms.pop()
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            for (child in bone.childBones) {
                renderRecursivelyRoutedFiltered(
                    ms = ms,
                    bone = child,
                    bufferSelector = bufferSelector,
                    potentialAnimatedBones = potentialAnimatedBones,
                    activeAnimatedBones = activeAnimatedBones,
                    mode = mode,
                    red = red,
                    green = green,
                    blue = blue,
                    alpha = alpha,
                    bloom = bloomNow,
                    transparent = transparentNow,
                    parentPotential = inPotentialSubtree,
                    parentDynamic = inDynamicSubtree,
                    scratch = scratch,
                )
            }
        }

        ms.pop()
    }

    private fun renderQuad(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        cube: GeoCube,
        quad: GeoQuad,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ) {
        val normal = scratch.normal
        normal.set(
            quad.normal.x.toFloat(),
            quad.normal.y.toFloat(),
            quad.normal.z.toFloat(),
        )

        // Manual normal matrix multiplication (avoid allocations / virtual calls).
        val n = ms.normalMatrix
        val nx = normal.x
        val ny = normal.y
        val nz = normal.z
        normal.x = n.m00 * nx + n.m01 * ny + n.m02 * nz
        normal.y = n.m10 * nx + n.m11 * ny + n.m12 * nz
        normal.z = n.m20 * nx + n.m21 * ny + n.m22 * nz

        // Fix dark shading for flat cubes (mirrors GeckoLib behavior)
        if ((cube.size.y == 0f || cube.size.z == 0f) && normal.x < 0) normal.x *= -1
        if ((cube.size.x == 0f || cube.size.z == 0f) && normal.y < 0) normal.y *= -1
        if ((cube.size.x == 0f || cube.size.y == 0f) && normal.z < 0) normal.z *= -1

        for (v in quad.vertices) {
            emitVertex(builder, ms, v, normal, red, green, blue, alpha)
        }
    }

    private fun emitVertex(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        vertex: GeoVertex,
        normal: Vector3f,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        // Manual model matrix multiplication (hot path).
        // Equivalent to: ms.modelMatrix.transform(Vector4f(x,y,z,1))
        val m = ms.modelMatrix
        val x = vertex.position.x
        val y = vertex.position.y
        val z = vertex.position.z

        val tx = m.m00 * x + m.m01 * y + m.m02 * z + m.m03
        val ty = m.m10 * x + m.m11 * y + m.m12 * z + m.m13
        val tz = m.m20 * x + m.m21 * y + m.m22 * z + m.m23

        builder
            .pos(tx.toDouble(), ty.toDouble(), tz.toDouble())
            .tex(vertex.textureU.toDouble(), vertex.textureV.toDouble())
            .color(red, green, blue, alpha)
            .normal(normal.x, normal.y, normal.z)
            .endVertex()
    }

    private fun renderCube(
        builder: BufferBuilder,
        ms: MmceMatrixStack,
        cube: GeoCube,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        scratch: Scratch,
    ) {
        ms.moveToPivot(cube)
        ms.rotate(cube)
        ms.moveBackFromPivot(cube)

        for (quad in cube.quads) {
            if (quad == null) continue
            renderQuad(builder, ms, cube, quad, red, green, blue, alpha, scratch)
        }
    }
}
