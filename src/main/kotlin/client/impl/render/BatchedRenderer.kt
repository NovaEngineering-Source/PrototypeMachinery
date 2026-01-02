package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.api.render.Renderable
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderBuildTask
import github.kasuminova.prototypemachinery.client.impl.render.task.RenderTaskCache

/**
 * Facade for the batching renderer.
 *
 * Callers (TESR, preview widgets, etc.) provide a [Renderable] instance and a factory
 * that can build a suitable [RenderBuildTask].
 */
internal object BatchedRenderer {

    internal fun render(renderable: Renderable, taskFactory: () -> RenderBuildTask) {
        val task = RenderTaskCache.getOrSubmit(renderable, taskFactory)
        val built = task.takeBuilt() ?: return
        if (built.isEmpty()) return

        val texture = renderable.renderKey.textureId

        built.byPass.forEach { (pass, buffer) ->
            val light = when (pass) {
                RenderPass.BLOOM, RenderPass.BLOOM_TRANSPARENT -> -1
                else -> renderable.combinedLight
            }
            RenderManager.addBuffer(pass, texture, light, buffer)
        }

        built.packedByPass.forEach { (pass, batch) ->
            val light = when (pass) {
                RenderPass.BLOOM, RenderPass.BLOOM_TRANSPARENT -> -1
                else -> renderable.combinedLight
            }
            RenderManager.addPacked(pass, texture, light, batch)
        }

        built.gpuByPass.forEach { (pass, draw) ->
            val light = when (pass) {
                RenderPass.BLOOM, RenderPass.BLOOM_TRANSPARENT -> -1
                else -> renderable.combinedLight
            }
            RenderManager.addGpu(pass, texture, light, draw)
        }
    }
}
