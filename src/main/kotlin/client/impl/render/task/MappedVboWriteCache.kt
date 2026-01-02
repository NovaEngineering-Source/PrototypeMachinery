package github.kasuminova.prototypemachinery.client.impl.render.task

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.RenderFrameClock
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.client.renderer.vertex.VertexFormat
import org.lwjgl.opengl.ARBMapBufferRange
import org.lwjgl.opengl.ContextCapabilities
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL30
import org.lwjgl.opengl.GLContext
import java.nio.ByteBuffer

/**
 * Simple per-owner A/B VBO cache for "map back buffer -> write -> unmap -> swap" workflows.
 *
 * IMPORTANT:
 * - All functions must be called on the Minecraft main thread (GL context owner).
 * - This is intentionally generic so future pipelines (e.g. OBJ) can reuse it.
 */
internal object MappedVboWriteCache {

    internal data class Key(
        val ownerKey: Any,
        val pass: RenderPass,
        val format: VertexFormat,
        val drawMode: Int,
    )

    internal data class Entry(
        var front: VertexBuffer,
        var back: VertexBuffer,
        val format: VertexFormat,
        val drawMode: Int,
        var lastUsedFrameId: Int,
    )

    internal data class Ticket(
        val key: Key,
        val entry: Entry,
        val backVbo: VertexBuffer,
        val mapped: ByteBuffer,
        val intView: java.nio.IntBuffer,
        val capacityBytes: Int,
    )

    private val map: MutableMap<Key, Entry> = HashMap()

    private fun glCaps(): ContextCapabilities = GLContext.getCapabilities()

    internal fun tryMapForWrite(key: Key, bytesWanted: Int): Ticket? {
        if (bytesWanted <= 0) return null

        val caps = glCaps()
        val supportsMap = caps.OpenGL30 || caps.GL_ARB_map_buffer_range
        if (!supportsMap) return null

        val frameId = RenderFrameClock.getFrameId()

        val entry = map.getOrPut(key) {
            Entry(
                front = VertexBuffer(key.format),
                back = VertexBuffer(key.format),
                format = key.format,
                drawMode = key.drawMode,
                lastUsedFrameId = frameId,
            )
        }

        // If the format/drawMode changes for the same key, drop and recreate.
        if (entry.format != key.format || entry.drawMode != key.drawMode) {
            runCatching { entry.front.deleteGlBuffers() }
            runCatching { entry.back.deleteGlBuffers() }
            val fresh = Entry(
                front = VertexBuffer(key.format),
                back = VertexBuffer(key.format),
                format = key.format,
                drawMode = key.drawMode,
                lastUsedFrameId = frameId,
            )
            map[key] = fresh
            return tryMapForWrite(key, bytesWanted)
        }

        entry.lastUsedFrameId = frameId

        val vbo = entry.back
        vbo.bindBuffer()
        try {
            // orphan + reserve
            val usage = GL15.GL_STREAM_DRAW
            GL15.glBufferData(OpenGlHelper.GL_ARRAY_BUFFER, bytesWanted.toLong(), usage)

            val access = GL30.GL_MAP_WRITE_BIT or GL30.GL_MAP_INVALIDATE_BUFFER_BIT or GL30.GL_MAP_UNSYNCHRONIZED_BIT
            val mapped: ByteBuffer? = if (caps.OpenGL30) {
                GL30.glMapBufferRange(OpenGlHelper.GL_ARRAY_BUFFER, 0L, bytesWanted.toLong(), access, null as ByteBuffer?)
            } else {
                ARBMapBufferRange.glMapBufferRange(OpenGlHelper.GL_ARRAY_BUFFER, 0L, bytesWanted.toLong(), access, null as ByteBuffer?)
            }

            if (mapped == null) {
                return null
            }

            mapped.clear()
            mapped.limit(bytesWanted)
            mapped.position(0)

            val intView = mapped.asIntBuffer()
            return Ticket(
                key = key,
                entry = entry,
                backVbo = vbo,
                mapped = mapped,
                intView = intView,
                capacityBytes = bytesWanted,
            )
        } catch (_: Throwable) {
            // If anything goes wrong, unbind and bail.
            return null
        } finally {
            vbo.unbindBuffer()
        }
    }

    internal fun finishWrite(ticket: Ticket, bytesWritten: Int, vertexCount: Int): GpuBucketDraw? {
        val entry = ticket.entry
        val vbo = ticket.backVbo

        vbo.bindBuffer()
        try {
            val ok = GL15.glUnmapBuffer(OpenGlHelper.GL_ARRAY_BUFFER)
            if (!ok) {
                return null
            }
        } catch (_: Throwable) {
            return null
        } finally {
            vbo.unbindBuffer()
        }

        // Swap: newly written back becomes front.
        val tmp = entry.front
        entry.front = entry.back
        entry.back = tmp

        return GpuBucketDraw(
            format = ticket.key.format,
            drawMode = ticket.key.drawMode,
            vertexCount = vertexCount,
            totalBytes = bytesWritten,
            vbo = entry.front,
        )
    }

    internal fun clearAll(reason: String) {
        if (map.isEmpty()) return
        val entries = map.values.toList()
        map.clear()

        for (e in entries) {
            runCatching { e.front.deleteGlBuffers() }
            runCatching { e.back.deleteGlBuffers() }
        }
    }
}
