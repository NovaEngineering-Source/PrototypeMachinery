package github.kasuminova.prototypemachinery.client.impl.render.task

import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.client.renderer.vertex.VertexFormat

/**
 * Represents geometry that is already resident in a GPU VBO.
 *
 * This is primarily intended for "build directly into mapped VBO" pipelines.
 */
internal data class GpuBucketDraw(
    val format: VertexFormat,
    val drawMode: Int,
    val vertexCount: Int,
    val totalBytes: Int,
    val vbo: VertexBuffer,
    /** Optional disposer invoked when the containing BuiltBuffers is disposed. */
    val dispose: (() -> Unit)? = null,
)
