package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.api.tuning.RenderTuning
import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.assets.ExternalDiskTextureBinder
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomBridge
import github.kasuminova.prototypemachinery.client.impl.render.task.BuiltBuffers
import github.kasuminova.prototypemachinery.client.util.OpaqueChunkVboCache
import github.kasuminova.prototypemachinery.client.util.ReusableVboUploader
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher
import net.minecraft.util.ResourceLocation
import org.lwjgl.opengl.GL11
import java.nio.ByteBuffer

/**
 * Centralized render dispatcher for machine models.
 *
 * Collects render data from all machine TESRs during the TESR batch phase,
 * then renders everything in correct pass order after all TESRs have executed.
 *
 * This architecture enables:
 * 1. Correct transparent rendering order across all machines
 * 2. Future multi-threaded buffer building
 * 3. Proper integration with GT bloom pipeline
 */
internal object MachineRenderDispatcher {

    private val uploader = ReusableVboUploader()

    private val opaqueChunkVboCache = OpaqueChunkVboCache()

    /**
     * Per-frame collected render data from all machine TESRs.
     * Cleared after each flush.
     */
    private val pendingRenders = mutableListOf<PendingRenderData>()

    /**
     * Data class holding render information for a single machine/sub-structure.
     */
    data class PendingRenderData(
        val texture: ResourceLocation,
        val combinedLight: Int,
        val chunkX: Int,
        val chunkZ: Int,
        val built: BuiltBuffers,
    )

    /**
     * Called by TESR to submit render data for deferred batch rendering.
     */
    fun submit(data: PendingRenderData) {
        pendingRenders.add(data)
    }

    /**
     * Called by TESR to submit multiple render data entries.
     */
    fun submitAll(dataList: List<PendingRenderData>) {
        pendingRenders.addAll(dataList)
    }

    /**
     * Returns true if there are pending renders to flush.
     */
    fun hasPendingWork(): Boolean = pendingRenders.isNotEmpty()

    internal fun opaqueChunkCacheStats(): OpaqueChunkVboCache.StatsSnapshot {
        return opaqueChunkVboCache.statsSnapshot()
    }

    /**
     * Clear all pending render data and release any reusable GL resources.
     *
     * Intended for world unload / resource reload.
     */
    fun clearAll() {
        pendingRenders.clear()
        uploader.dispose()
        opaqueChunkVboCache.clearAll("dispatcher_clearAll")
    }

    /**
     * Flush all pending renders in correct pass order.
     *
     * Called by Mixin after TileEntityRendererDispatcher.drawBatch() completes.
     * This ensures all machine DEFAULT passes render first, then all TRANSPARENT.
     */
    fun flush() {
        if (pendingRenders.isEmpty()) {
            return
        }

        val dataList = pendingRenders.toList()
        pendingRenders.clear()

        RenderStats.noteDispatcherPending(dataList.size)

        if (opaqueChunkVboCache.enabled()) {
            opaqueChunkVboCache.nextFrame()
        }

        val deferBloomToPost = GregTechBloomBridge.isEnabled

        GlStateManager.pushMatrix()
        // IMPORTANT:
        // Our VBOs are baked in absolute world coordinates (we use te.pos.x/y/z in the bake task).
        // When drawing here (after TESR batch), the current modelview may NOT have the camera-origin
        // translation applied. RenderManager (used by the GT bloom callback) always applies -camera.
        // If we don't do the same here, DEFAULT/TRANSPARENT will appear offset while BLOOM looks correct.
        GlStateManager.translate(
            -TileEntityRendererDispatcher.staticPlayerX,
            -TileEntityRendererDispatcher.staticPlayerY,
            -TileEntityRendererDispatcher.staticPlayerZ
        )

        try {
            withLightmapEnabled {
                // Phase 1: Render all DEFAULT (opaque) passes
                renderPassBatched(dataList, RenderPass.DEFAULT, applyLightmap = true)

                // Phase 2: Render all TRANSPARENT passes
                RenderTypeState.pre(RenderPass.TRANSPARENT)
                renderPassWithoutState(dataList, RenderPass.TRANSPARENT)
                RenderTypeState.post(RenderPass.TRANSPARENT)

                // Phase 3: Handle BLOOM passes
                if (deferBloomToPost) {
                    // Defer to RenderManager for GT bloom callback
                    for (data in dataList) {
                        data.built.byPass[RenderPass.BLOOM]?.let { buffer ->
                            RenderManager.addBuffer(RenderPass.BLOOM, data.texture, -1, buffer)
                        }
                        data.built.byPass[RenderPass.BLOOM_TRANSPARENT]?.let { buffer ->
                            RenderManager.addBuffer(RenderPass.BLOOM_TRANSPARENT, data.texture, -1, buffer)
                        }
                    }
                } else {
                    // No GT bloom: render directly
                    RenderTypeState.pre(RenderPass.BLOOM)
                    renderPassBatched(dataList, RenderPass.BLOOM, applyLightmap = false)
                    RenderTypeState.post(RenderPass.BLOOM)

                    RenderTypeState.pre(RenderPass.BLOOM_TRANSPARENT)
                    renderPassBatched(dataList, RenderPass.BLOOM_TRANSPARENT, applyLightmap = false)
                    RenderTypeState.post(RenderPass.BLOOM_TRANSPARENT)
                }
            }
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
        }
    }

    /**
     * Batched renderer for passes where draw order does NOT affect correctness.
     *
     * We bucket by texture + combinedLight so we can reduce state changes and glDrawArrays calls
     * (via [ReusableVboUploader.drawMultiple]).
     */
    private fun renderPassBatched(dataList: List<PendingRenderData>, pass: RenderPass, applyLightmap: Boolean) {
        if (dataList.isEmpty()) return

        // Experimental: chunk-group VBO cache for DEFAULT only.
        if (pass == RenderPass.DEFAULT && applyLightmap && opaqueChunkVboCache.enabled()) {
            renderOpaquePassChunkCached(dataList)
            return
        }

        // Texture -> Light -> Buffers
        val buckets: MutableMap<ResourceLocation, Int2ObjectOpenHashMap<MutableList<BufferBuilder>>> =
            Object2ObjectOpenHashMap()

        for (data in dataList) {
            val buffer = data.built.byPass[pass] ?: continue
            val light = if (applyLightmap) data.combinedLight else -1
            buckets
                .computeIfAbsent(data.texture) { Int2ObjectOpenHashMap() }
                .computeIfAbsent(light) { ObjectArrayList() }
                .add(buffer)
        }

        if (buckets.isEmpty()) return

        buckets.forEach { (texture, lightMap) ->
            RenderStats.addTextureBind()
            ExternalDiskTextureBinder.bind(texture)
            lightMap.forEach { (light, bufferList) ->
                if (applyLightmap) {
                    setLightmapCoords(light)
                }
                RenderStats.noteMergeBucket(bufferList.size)
                uploader.drawMultiple(bufferList)
            }
        }
    }

    /**
        * Opaque-only batched renderer with chunk-group VBO cache.
        *
        * Buckets by texture -> combinedLight -> chunkGroupKey.
        */
    private fun renderOpaquePassChunkCached(dataList: List<PendingRenderData>) {
        // Texture -> Light -> ChunkGroup -> Buffers
        val buckets: MutableMap<ResourceLocation, Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<MutableList<BufferBuilder>>>> =
            Object2ObjectOpenHashMap()

        for (data in dataList) {
            val buffer = data.built.byPass[RenderPass.DEFAULT] ?: continue
            val light = data.combinedLight
            val chunkGroupKey = computeChunkGroupKey(data.chunkX, data.chunkZ)

            val lightMap = buckets[data.texture] ?: run {
                val created: Int2ObjectOpenHashMap<Long2ObjectOpenHashMap<MutableList<BufferBuilder>>> = Int2ObjectOpenHashMap()
                buckets[data.texture] = created
                created
            }

            val chunkMap = lightMap.get(light) ?: run {
                val created: Long2ObjectOpenHashMap<MutableList<BufferBuilder>> = Long2ObjectOpenHashMap()
                lightMap.put(light, created)
                created
            }
            val existing = chunkMap.get(chunkGroupKey)
            if (existing != null) {
                existing.add(buffer)
            } else {
                val list: MutableList<BufferBuilder> = ObjectArrayList()
                list.add(buffer)
                chunkMap.put(chunkGroupKey, list)
            }
        }

        if (buckets.isEmpty()) return

        val minBuffers = RenderTuning.opaqueChunkVboCacheMinBuffers
        val minBytes = RenderTuning.opaqueChunkVboCacheMinBytes

        buckets.forEach { (texture, lightMap) ->
            RenderStats.addTextureBind()
            ExternalDiskTextureBinder.bind(texture)

            lightMap.forEach { (light, chunkMap) ->
                setLightmapCoords(light)

                chunkMap.forEach { (chunkGroupKey, bufferList) ->
                    if (bufferList.isEmpty()) return@forEach

                    // Cheap totals + fingerprint.
                    val first = bufferList[0]
                    val format = first.vertexFormat
                    val drawMode = first.drawMode
                    val vertexSize = format.size

                    var totalVertexCount = 0
                    var totalBytes = 0

                    // Fingerprint: order-insensitive multiset hash of participating buffers.
                    // We intentionally avoid BufferBuilder object identity because builders may be pooled/reused,
                    // and iteration order can vary. Instead, we sample a few ints from each buffer's actual data.
                    var fpSeed = -3750763034362895579L // random-ish constant
                    fpSeed = mix64(fpSeed xor chunkGroupKey)
                    fpSeed = mix64(fpSeed xor light.toLong())
                    fpSeed = mix64(fpSeed xor drawMode.toLong())
                    fpSeed = mix64(fpSeed xor vertexSize.toLong())

                    var sum = 0L
                    var x = 0L
                    var usedBuffers = 0

                    for (b in bufferList) {
                        val vc = b.vertexCount
                        if (vc <= 0) continue
                        val bytesUsed = vc * vertexSize
                        if (bytesUsed <= 0) continue

                        totalVertexCount += vc
                        totalBytes += bytesUsed

                        // Per-buffer hash: sample vertex data + include vertexCount.
                        val h = mix64(sampleBufferHash(b.byteBuffer, bytesUsed) xor vc.toLong())
                        sum += h
                        x = x xor java.lang.Long.rotateLeft(h, 17)
                        usedBuffers++
                    }

                    val fp = mix64(fpSeed xor sum xor x xor usedBuffers.toLong() xor totalVertexCount.toLong() xor totalBytes.toLong())

                    if (totalVertexCount <= 0 || totalBytes <= 0) return@forEach

                    RenderStats.noteMergeBucket(bufferList.size)

                    // Heuristic: only use chunk cache for non-trivial buckets.
                    if (bufferList.size < minBuffers || totalBytes < minBytes) {
                        uploader.drawMultiple(bufferList)
                        return@forEach
                    }

                    val key = OpaqueChunkVboCache.Key(
                        chunkGroupKey = chunkGroupKey,
                        texture = texture,
                        combinedLight = light,
                    )

                    val entry = opaqueChunkVboCache.getOrRebuildOrNull(
                        key = key,
                        format = format,
                        drawMode = drawMode,
                        bytesUsed = totalBytes,
                        vertexCount = totalVertexCount,
                        fingerprint = fp,
                    ) { vbo ->
                        // Rebuild path: merge into scratch + upload to this chunk VBO.
                        return@getOrRebuildOrNull uploader.withMergedScratch(bufferList) { merge ->
                            // Track memcpy+merge cost only when we actually rebuild.
                            RenderStats.addMerge(bufferList.size, merge.totalBytes)
                            RenderStats.addOpaqueChunkCacheUpload(merge.totalBytes)
                            // Still counts as a VBO upload in the global stats.
                            RenderStats.addVboUpload(merge.totalBytes)
                            vbo.bufferData(merge.data)
                        }
                    }

                    if (entry != null) {
                        uploader.drawVbo(entry.vbo, entry.format, entry.drawMode, entry.vertexCount)
                    } else {
                        // Incompatible builders or merge failure.
                        uploader.drawMultiple(bufferList)
                    }
                }
            }
        }
    }

    private fun sampleBufferHash(buf: ByteBuffer, bytesUsed: Int): Long {
        if (bytesUsed <= 0) return 0L
        // Duplicate to avoid touching original position/limit.
        val dup = buf.duplicate().also {
            it.clear()
            it.limit(bytesUsed)
        }

        // Work on int view (vertex format here is int-packed hot path; even if not, this is still a stable byte pattern).
        val ib = dup.asIntBuffer()
        val n = ib.limit()
        if (n <= 0) return mix64(bytesUsed.toLong())

        // 0x9E3779B97F4A7C15 as signed long (Kotlin 1.2 has no unsigned literals)
        var h = -7046029254386353131L

        fun get(i: Int): Long = (ib.get(i).toLong() and 0xFFFF_FFFFL)

        val last = n - 1
        val mid = last ushr 1

        // Always sample a few stable positions.
        h = mix64(h xor get(0))
        if (n > 1) h = mix64(h xor get(1))
        if (n > 2) h = mix64(h xor get(2))
        if (n > 3) h = mix64(h xor get(3))

        h = mix64(h xor get(mid))
        if (last != mid) h = mix64(h xor get(last))
        if (last - 1 > 3) h = mix64(h xor get(last - 1))
        if (last - 2 > 3) h = mix64(h xor get(last - 2))

        // Include length to reduce collisions.
        h = mix64(h xor (bytesUsed.toLong() shl 1) xor n.toLong())
        return h
    }

    // 64-bit mix (avalanche), based on MurmurHash3 finalizer.
    private fun mix64(z: Long): Long {
        var x = z
        x = (x xor (x ushr 33)) * -49064778989728563L
        x = (x xor (x ushr 33)) * -4265267296055464877L
        x = x xor (x ushr 33)
        return x
    }

    private fun computeChunkGroupKey(chunkX: Int, chunkZ: Int): Long {
        val size = RenderTuning.opaqueChunkVboCacheChunkSize
        val gx = Math.floorDiv(chunkX, size)
        val gz = Math.floorDiv(chunkZ, size)
        return (gx.toLong() shl 32) xor (gz.toLong() and 0xFFFF_FFFFL)
    }

    private fun renderPassWithoutState(dataList: List<PendingRenderData>, pass: RenderPass) {
        for (data in dataList) {
            data.built.byPass[pass]?.let { buffer ->
                RenderStats.addTextureBind()
                ExternalDiskTextureBinder.bind(data.texture)
                if (pass != RenderPass.BLOOM && pass != RenderPass.BLOOM_TRANSPARENT) {
                    setLightmapCoords(data.combinedLight)
                }
                uploader.draw(buffer)
            }
        }
    }

    private fun setLightmapCoords(combinedLight: Int) {
        if (combinedLight != -1) {
            val lx = combinedLight % 65536
            val ly = combinedLight / 65536
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx.toFloat(), ly.toFloat())
            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
        }
    }

    private inline fun <T> withLightmapEnabled(block: () -> T): T {
        val mc = Minecraft.getMinecraft()

        // Snapshot lightmap state
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
        val wasEnabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
        val prevBinding = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)

        GL11.glMatrixMode(GL11.GL_TEXTURE)
        GL11.glPushMatrix()
        GL11.glMatrixMode(GL11.GL_MODELVIEW)

        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)

        // Enable vanilla lightmap
        mc.entityRenderer.enableLightmap()

        return try {
            block()
        } finally {
            // Restore lightmap state
            GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)

            GL11.glMatrixMode(GL11.GL_TEXTURE)
            GL11.glPopMatrix()
            GL11.glMatrixMode(GL11.GL_MODELVIEW)

            GlStateManager.bindTexture(prevBinding)

            if (!wasEnabled) {
                GlStateManager.disableTexture2D()
            } else {
                GlStateManager.enableTexture2D()
            }

            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
        }
    }
}
