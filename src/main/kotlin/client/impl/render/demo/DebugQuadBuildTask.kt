package github.kasuminova.prototypemachinery.client.impl.render.demo

import github.kasuminova.prototypemachinery.client.api.render.RenderKey
import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderBuildTask
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import org.lwjgl.opengl.GL11

/**
 * Small "known-good" build task that generates a single colored quad.
 *
 * This is useful for validating the batching pipeline without involving any model loaders.
 *
 * NOTE: This task reads fields from [Renderable] on a background thread. For production tasks,
 * prefer copying all necessary values into an immutable snapshot on the render thread.
 */
internal class DebugQuadBuildTask(
    private val renderable: Renderable,
) : RenderBuildTask(renderable.ownerKey) {

    override fun currentKey(): RenderKey = renderable.renderKey

    override fun build(key: RenderKey): BuiltBuffers {
        val builder = BufferBuilder(256)

        // Slightly above the block top to avoid z-fighting.
        val x = renderable.x
        val y = renderable.y + 1.001
        val z = renderable.z

        builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)

        builder.pos(x + 0.0, y, z + 0.0).color(255, 0, 0, 180).endVertex()
        builder.pos(x + 1.0, y, z + 0.0).color(255, 0, 0, 180).endVertex()
        builder.pos(x + 1.0, y, z + 1.0).color(255, 0, 0, 180).endVertex()
        builder.pos(x + 0.0, y, z + 1.0).color(255, 0, 0, 180).endVertex()

        builder.finishDrawing()

        return BuiltBuffers(
            byPass = mapOf(RenderPass.DEFAULT to builder),
        )
    }
}
