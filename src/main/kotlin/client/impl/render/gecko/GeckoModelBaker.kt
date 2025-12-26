package github.kasuminova.prototypemachinery.client.impl.render.gecko

import net.minecraft.client.renderer.BufferBuilder
import software.bernie.geckolib3.geo.render.built.GeoBone
import software.bernie.geckolib3.geo.render.built.GeoCube
import software.bernie.geckolib3.geo.render.built.GeoModel
import software.bernie.geckolib3.geo.render.built.GeoQuad
import software.bernie.geckolib3.geo.render.built.GeoVertex
import software.bernie.geckolib3.util.MatrixStack
import javax.vecmath.Vector3f
import javax.vecmath.Vector4f

/**
 * CPU-side baker that converts GeckoLib built models into a [BufferBuilder].
 *
 * This is adapted from GeckoLib's `IGeoRenderer` but:
 * - avoids Tessellator/GlStateManager
 * - uses a per-bake [MatrixStack] (thread-safe)
 */
internal object GeckoModelBaker {

    internal fun bake(
        model: GeoModel,
        builder: BufferBuilder,
        matrixStack: MatrixStack,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
        for (bone in model.topLevelBones) {
            renderRecursively(builder, matrixStack, bone, red, green, blue, alpha)
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
        matrixStack: MatrixStack,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        red: Float = 1.0f,
        green: Float = 1.0f,
        blue: Float = 1.0f,
        alpha: Float = 1.0f,
    ) {
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
            )
        }
    }

    private fun renderRecursively(
        builder: BufferBuilder,
        ms: MatrixStack,
        bone: GeoBone,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
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
                renderCube(builder, ms, cube, red, green, blue, alpha)
                ms.pop()
            }
        }

        if (!bone.childBonesAreHiddenToo()) {
            for (child in bone.childBones) {
                renderRecursively(builder, ms, child, red, green, blue, alpha)
            }
        }

        ms.pop()
    }

    private fun renderCube(
        builder: BufferBuilder,
        ms: MatrixStack,
        cube: GeoCube,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        ms.moveToPivot(cube)
        ms.rotate(cube)
        ms.moveBackFromPivot(cube)

        for (quad in cube.quads) {
            if (quad == null) continue
            renderQuad(builder, ms, cube, quad, red, green, blue, alpha)
        }
    }

    private fun renderRecursivelyRouted(
        ms: MatrixStack,
        bone: GeoBone,
        bufferSelector: (bloom: Boolean, transparent: Boolean) -> BufferBuilder,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        bloom: Boolean,
        transparent: Boolean,
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
                renderCube(builder, ms, cube, red, green, blue, alpha)
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
                )
            }
        }

        ms.pop()
    }

    private fun renderQuad(
        builder: BufferBuilder,
        ms: MatrixStack,
        cube: GeoCube,
        quad: GeoQuad,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        val normal = Vector3f(
            quad.normal.x.toFloat(),
            quad.normal.y.toFloat(),
            quad.normal.z.toFloat(),
        )

        ms.normalMatrix.transform(normal)

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
        ms: MatrixStack,
        vertex: GeoVertex,
        normal: Vector3f,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
    ) {
        val vec4 = Vector4f(vertex.position.x, vertex.position.y, vertex.position.z, 1.0f)
        ms.modelMatrix.transform(vec4)

        builder
            .pos(vec4.x.toDouble(), vec4.y.toDouble(), vec4.z.toDouble())
            .tex(vertex.textureU.toDouble(), vertex.textureV.toDouble())
            .color(red, green, blue, alpha)
            .normal(normal.x, normal.y, normal.z)
            .endVertex()
    }
}
