package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.client.api.render.RenderPass
import github.kasuminova.prototypemachinery.client.impl.render.assets.ExternalDiskTextureBinder
import github.kasuminova.prototypemachinery.client.impl.render.bloom.GregTechBloomBridge
import github.kasuminova.prototypemachinery.client.impl.render.task.GpuBucketDraw
import github.kasuminova.prototypemachinery.client.impl.render.task.PackedBucketBatch
import github.kasuminova.prototypemachinery.client.util.ReusableVboUploader
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher
import net.minecraft.util.ResourceLocation
import java.util.EnumMap

/**
 * Collects buffers (often produced off-thread) and draws them in deterministic batches.
 *
 * 收敛目标：只保留两条主路径
 * - 回退：BufferBuilder（可选 PackedBucketBatch）
 * - mapped：GpuBucketDraw（由 mapped VBO 构建任务产出）
 */
internal object RenderManager {

    private val uploader = ReusableVboUploader()

    // RenderPass -> Texture -> Light -> Buffers
    private val buckets: MutableMap<RenderPass, MutableMap<ResourceLocation, Int2ObjectOpenHashMap<MutableList<BufferBuilder>>>> =
        EnumMap(RenderPass::class.java)

    // RenderPass -> Texture -> Light -> Packed batches
    private val packedBuckets: MutableMap<RenderPass, MutableMap<ResourceLocation, Int2ObjectOpenHashMap<MutableList<PackedBucketBatch>>>> =
        EnumMap(RenderPass::class.java)

    // RenderPass -> Texture -> Light -> GPU VBO draws
    private val gpuBuckets: MutableMap<RenderPass, MutableMap<ResourceLocation, Int2ObjectOpenHashMap<MutableList<GpuBucketDraw>>>> =
        EnumMap(RenderPass::class.java)

    fun addBuffer(pass: RenderPass, texture: ResourceLocation, combinedLight: Int, buffer: BufferBuilder) {
        buckets
            .computeIfAbsent(pass) { Object2ObjectOpenHashMap() }
            .computeIfAbsent(texture) { Int2ObjectOpenHashMap() }
            .computeIfAbsent(combinedLight) { ObjectArrayList() }
            .add(buffer)
    }

    fun addPacked(pass: RenderPass, texture: ResourceLocation, combinedLight: Int, batch: PackedBucketBatch) {
        packedBuckets
            .computeIfAbsent(pass) { Object2ObjectOpenHashMap() }
            .computeIfAbsent(texture) { Int2ObjectOpenHashMap() }
            .computeIfAbsent(combinedLight) { ObjectArrayList() }
            .add(batch)
    }

    fun addGpu(pass: RenderPass, texture: ResourceLocation, combinedLight: Int, draw: GpuBucketDraw) {
        gpuBuckets
            .computeIfAbsent(pass) { Object2ObjectOpenHashMap() }
            .computeIfAbsent(texture) { Int2ObjectOpenHashMap() }
            .computeIfAbsent(combinedLight) { ObjectArrayList() }
            .add(draw)
    }

    /**
     * Clear all queued buffers and release reusable GL resources.
     *
     * 必须在渲染线程调用（会删除 GL 对象）。
     */
    fun clearAll() {
        buckets.clear()
        packedBuckets.clear()
        gpuBuckets.clear()
        uploader.dispose()
    }

    /**
     * Draw all queued buffers.
     *
     * 当 GT/Lumenized bloom 可用时：BLOOM passes 会留给 bloom callback；否则当作普通 pass 直接画掉。
     */
    fun drawAll() {
        if (buckets.isEmpty() && packedBuckets.isEmpty() && gpuBuckets.isEmpty()) return

        RenderStats.noteRenderManagerBuckets(
            buckets.size + packedBuckets.size + gpuBuckets.size
        )

        val mc = Minecraft.getMinecraft()
        val entityRenderer = mc.entityRenderer

        entityRenderer.enableLightmap()
        GlStateManager.pushMatrix()
        GlStateManager.translate(
            -TileEntityRendererDispatcher.staticPlayerX,
            -TileEntityRendererDispatcher.staticPlayerY,
            -TileEntityRendererDispatcher.staticPlayerZ
        )

        try {
            for (pass in collectPassesSorted()) {
                if (shouldDeferToBloomCallback(pass)) continue
                drawOnePass(pass, clearAfterDraw = true)
            }
        } finally {
            mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
            entityRenderer.disableLightmap()
        }
    }

    internal fun hasPendingBloomWork(): Boolean {
        if (buckets.isEmpty() && packedBuckets.isEmpty() && gpuBuckets.isEmpty()) return false
        return buckets.keys.any(::isBloomPass) || packedBuckets.keys.any(::isBloomPass) || gpuBuckets.keys.any(::isBloomPass)
    }

    /**
     * Draw bloom-capable passes.
     *
     * When GregTech bloom post-processing is present, this is invoked from its callback.
     */
    internal fun drawBloomPasses(clearAfterDraw: Boolean) {
        if (!hasPendingBloomWork()) return

        // GT bloom 回调内部会自己 push/pop matrix；这里仍做一次 camera-origin 平移即可。
        GlStateManager.pushMatrix()
        GlStateManager.translate(
            -TileEntityRendererDispatcher.staticPlayerX,
            -TileEntityRendererDispatcher.staticPlayerY,
            -TileEntityRendererDispatcher.staticPlayerZ
        )

        try {
            for (pass in collectPassesSorted()) {
                if (!isBloomPass(pass)) continue
                drawOnePass(pass, clearAfterDraw = clearAfterDraw)
            }
        } finally {
            Minecraft.getMinecraft().renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
            GlStateManager.popMatrix()
        }
    }

    private fun collectPassesSorted(): List<RenderPass> {
        val set = LinkedHashSet<RenderPass>(8)
        set.addAll(buckets.keys)
        set.addAll(packedBuckets.keys)
        set.addAll(gpuBuckets.keys)
        return set.sortedBy { it.ordinal }
    }

    private fun isBloomPass(pass: RenderPass): Boolean {
        return pass == RenderPass.BLOOM || pass == RenderPass.BLOOM_TRANSPARENT
    }

    private fun shouldDeferToBloomCallback(pass: RenderPass): Boolean {
        return isBloomPass(pass) && GregTechBloomBridge.isEnabled
    }

    private fun drawOnePass(pass: RenderPass, clearAfterDraw: Boolean) {
        val textures = buckets[pass]
        val packedTextures = packedBuckets[pass]
        val gpuTextures = gpuBuckets[pass]

        val empty = (textures == null || textures.isEmpty()) &&
            (packedTextures == null || packedTextures.isEmpty()) &&
            (gpuTextures == null || gpuTextures.isEmpty())

        if (empty) {
            if (clearAfterDraw) {
                buckets.remove(pass)
                packedBuckets.remove(pass)
                gpuBuckets.remove(pass)
            }
            return
        }

        RenderTypeState.pre(pass)
        try {
            // BufferBuilder buckets (fallback)
            textures?.forEach { (texture, lightMap) ->
                RenderStats.addTextureBind()
                ExternalDiskTextureBinder.bind(texture)
                lightMap.forEach { (light, bufferList) ->
                    applyCombinedLight(light)
                    if (bufferList.isEmpty()) return@forEach
                    RenderStats.noteMergeBucket(bufferList.size)
                    uploader.drawMultiple(bufferList)
                }
            }

            // Packed buckets (optional pooled CPU buffers)
            packedTextures?.forEach { (texture, lightMap) ->
                RenderStats.addTextureBind()
                ExternalDiskTextureBinder.bind(texture)
                lightMap.forEach { (light, batchList) ->
                    applyCombinedLight(light)
                    if (batchList.isEmpty()) return@forEach
                    drawPackedBatchList(batchList)
                }
            }

            // GPU VBO draws (mapped path)
            gpuTextures?.forEach { (texture, lightMap) ->
                RenderStats.addTextureBind()
                ExternalDiskTextureBinder.bind(texture)
                lightMap.forEach { (light, drawList) ->
                    applyCombinedLight(light)
                    if (drawList.isEmpty()) return@forEach
                    for (d in drawList) {
                        if (d.vertexCount <= 0) continue
                        uploader.drawVbo(d.vbo, d.format, d.drawMode, d.vertexCount)
                    }
                }
            }
        } finally {
            RenderTypeState.post(pass)
        }

        if (clearAfterDraw) {
            buckets.remove(pass)
            packedBuckets.remove(pass)
            gpuBuckets.remove(pass)
        }
    }

    private fun applyCombinedLight(light: Int) {
        if (light == -1) return
        val lx = light % 65536
        val ly = light / 65536
        GlStateManager.setActiveTexture(OpenGlHelper.lightmapTexUnit)
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lx.toFloat(), ly.toFloat())
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit)
    }

    private fun drawPackedBatchList(batchList: MutableList<PackedBucketBatch>) {
        val first = batchList[0]
        val format = first.format
        val drawMode = first.drawMode

        // If the list contains mixed formats/modes, fall back to per-part draws to preserve correctness.
        for (b in batchList) {
            if (b.format != format || b.drawMode != drawMode) {
                for (bb in batchList) {
                    for (p in bb.parts) {
                        if (p.vertexCount <= 0 || p.totalBytes <= 0) continue
                        uploader.drawMergedByteBuffer(bb.format, bb.drawMode, p.vertexCount, p.totalBytes, p.data)
                    }
                }
                return
            }
        }

        var totalBytes = 0
        var totalVertices = 0

        // Capacity hint only; real segment count depends on parts.
        val segmentsTmp: MutableList<ReusableVboUploader.ByteBufferSegment> = ArrayList(batchList.size * 2)

        for (b in batchList) {
            for (p in b.parts) {
                if (p.totalBytes <= 0 || p.vertexCount <= 0) continue
                segmentsTmp.add(ReusableVboUploader.ByteBufferSegment(p.data, totalBytes, p.totalBytes))
                totalBytes += p.totalBytes
                totalVertices += p.vertexCount
            }
        }

        if (totalVertices <= 0 || totalBytes <= 0 || segmentsTmp.isEmpty()) return

        RenderStats.noteMergeBucket(segmentsTmp.size)
        if (segmentsTmp.size == 1) {
            val s = segmentsTmp[0]
            uploader.drawMergedByteBuffer(format, drawMode, totalVertices, totalBytes, s.data)
        } else {
            uploader.drawMergedByteBufferSegments(format, drawMode, totalVertices, totalBytes, segmentsTmp.toTypedArray())
        }
    }
}
