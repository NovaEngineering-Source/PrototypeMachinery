package github.kasuminova.prototypemachinery.client.preview

import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.client.util.BufferBuilderPool
import github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder
import net.minecraft.block.Block
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.init.Blocks
import net.minecraft.util.BlockRenderLayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.MathHelper
import net.minecraft.util.math.RayTraceResult
import net.minecraftforge.client.ForgeHooksClient
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.InputEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.lwjgl.input.Keyboard
import org.lwjgl.opengl.GL11
import java.lang.Math.floorDiv
import java.nio.ByteOrder
import java.util.EnumMap

/**
 * World projection renderer for structure preview.
 *
 * 结构预览的世界投影渲染器。
 *
 * Design goals:
 * - Follow player facing (discrete rotation) / 跟随玩家朝向（离散旋转）
 * - Avoid lag on huge structures via budgets (scan/render) / 通过预算避免超大结构卡顿
 */
internal object WorldProjectionManager {

    private data class CacheKey(
        val structureId: String,
        val orientation: StructureOrientation,
        val sliceCount: Int
    )

    private data class ChunkKey(val cx: Int, val cy: Int, val cz: Int)

    private data class ChunkMesh(
        val key: ChunkKey,
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double,
        val radiusSq: Double,
        val vbo: VertexBuffer,
        val approxBlockCount: Int
    )

    private data class BlockModelChunkMesh(
        val key: ChunkKey,
        val centerX: Double,
        val centerY: Double,
        val centerZ: Double,
        val radiusSq: Double,
        val solid: VertexBuffer?,
        val cutoutMipped: VertexBuffer?,
        val cutout: VertexBuffer?,
        val translucent: VertexBuffer?,
        val approxBlockCount: Int
    )

    private data class RenderCache(
        val key: CacheKey,
        val meshes: List<ChunkMesh>
    )

    private data class BlockModelRenderCache(
        val key: CacheKey,
        val meshes: List<BlockModelChunkMesh>
    )

    internal data class Entry(
        val rel: BlockPos,
        val requirement: github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
    )

    internal enum class Status { UNKNOWN, MATCH, MISMATCH, UNLOADED }

    private val modelCache = LinkedHashMap<CacheKey, StructurePreviewModel>(32, 0.75f, true)
    private val renderCache = LinkedHashMap<CacheKey, RenderCache>(16, 0.75f, true)
    private val blockModelRenderCache = LinkedHashMap<CacheKey, BlockModelRenderCache>(8, 0.75f, true)

    private var session: StructureProjectionSession? = null

    fun start(newSession: StructureProjectionSession) {
        session = newSession.copy()
    }

    fun stop() {
        session = null
    }

    /**
     * Called when structure registry is hot-reloaded.
     * Clears cached preview models/VBOs and marks the current session dirty.
     */
    fun onStructuresReloaded() {
        // Release GL resources held by caches.
        for (c in renderCache.values) {
            for (m in c.meshes) {
                runCatching { m.vbo.deleteGlBuffers() }
            }
        }
        for (c in blockModelRenderCache.values) {
            for (m in c.meshes) {
                runCatching { m.solid?.deleteGlBuffers() }
                runCatching { m.cutoutMipped?.deleteGlBuffers() }
                runCatching { m.cutout?.deleteGlBuffers() }
                runCatching { m.translucent?.deleteGlBuffers() }
            }
        }

        modelCache.clear()
        renderCache.clear()
        blockModelRenderCache.clear()

        session?.let {
            it.modelDirty = true
            it.invalidateEntries()
        }
    }

    @SubscribeEvent
    fun onKeyInput(event: InputEvent.KeyInputEvent) {
        val s = session ?: return
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return

        // Avoid triggering while typing in chat/GUI.
        if (mc.currentScreen != null) return

        if (ProjectionKeyBindings.toggleOrientationLock.isPressed) {
            val wasFollowing = s.followPlayerFacing
            if (wasFollowing) {
                // Lock current orientation.
                val desired = resolveDesiredOrientation(s, player)
                s.followPlayerFacing = false
                s.lockedOrientation = desired
                s.currentOrientation = desired
                s.invalidateModel()
                ProjectionHudRenderer.sendOrientationLockedMessage(player, desired.front, desired.top)
            } else {
                // Unlock: resume following player.
                s.followPlayerFacing = true
                s.lockedOrientation = null
                s.frontOverride = null
                s.currentOrientation = null
                s.invalidateModel()
                ProjectionHudRenderer.sendFollowingPlayerMessage(player)
            }
        }

        val rotatePos = ProjectionKeyBindings.rotatePositive.isPressed
        val rotateNeg = ProjectionKeyBindings.rotateNegative.isPressed
        if (rotatePos || rotateNeg) {
            // Determine axis by modifiers:
            // - none: yaw (UP)
            // - Shift: pitch (EAST)
            // - Ctrl: roll (SOUTH)
            val shift = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) || Keyboard.isKeyDown(Keyboard.KEY_RSHIFT)
            val ctrl = Keyboard.isKeyDown(Keyboard.KEY_LCONTROL) || Keyboard.isKeyDown(Keyboard.KEY_RCONTROL)

            val axis = when {
                shift -> EnumFacing.EAST
                ctrl -> EnumFacing.SOUTH
                else -> EnumFacing.UP
            }
            val rotAxis = if (rotatePos) axis else axis.opposite

            // Ensure we are in locked mode before rotating.
            val base = resolveDesiredOrientation(s, player)
            s.followPlayerFacing = false
            val next = base.rotate(rotAxis)
            s.lockedOrientation = next
            s.currentOrientation = next
            s.invalidateModel()
            ProjectionHudRenderer.sendOrientationRotatedMessage(player, next.front, next.top, axis)
        }
    }

    @SubscribeEvent
    fun onClientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return

        val s = session ?: return
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val world = mc.world ?: return

        // Update desired orientation.
        val desiredOrientation = resolveDesiredOrientation(s, player)
        if (desiredOrientation != s.currentOrientation) {
            s.currentOrientation = desiredOrientation
            s.invalidateModel()
        }

        // Update anchor if requested.
        s.anchorProvider?.invoke(mc)?.let { newAnchor ->
            if (newAnchor != s.anchor) {
                s.anchor = newAnchor
                // Anchor change does not require rebuilding the model (it is relative),
                // but does require invalidating cached statuses.
                s.invalidateStatuses()
            }
        }

        // Ensure model is loaded.
        val model = ensureModelLoaded(s) ?: return
        s.ensureEntries(model)

        // Incrementally update mismatch/match statuses to avoid spikes.
        val maxChecks = ProjectionConfig.MAX_STATUS_CHECKS_PER_TICK
        var checks = 0
        while (checks < maxChecks) {
            val idx = s.statusCursor
            if (idx >= s.entries.size) {
                s.statusCursor = 0
                break
            }

            val entry = s.entries[idx]
            val worldPos = s.anchor.add(entry.rel)

            val status = if (!world.isBlockLoaded(worldPos)) {
                Status.UNLOADED
            } else {
                when (val req = entry.requirement) {
                    is ExactBlockStateRequirement -> {
                        val state = world.getBlockState(worldPos)
                        val id = state.block.registryName
                        if (id == null) {
                            Status.UNKNOWN
                        } else {
                            @Suppress("DEPRECATION")
                            val meta = state.block.getMetaFromState(state)
                            if (id == req.blockId && meta == req.meta) Status.MATCH else Status.MISMATCH
                        }
                    }

                    else -> Status.UNKNOWN
                }
            }

            s.statuses[idx] = status

            s.statusCursor++
            checks++
        }
    }

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        val s = session ?: return
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val world = mc.world ?: return

        val model = ensureModelLoaded(s) ?: return
        s.ensureEntries(model)

        val partial = event.partialTicks
        val view = mc.renderViewEntity ?: player
        val camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partial
        val camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partial
        val camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partial

        val maxDist = (s.maxRenderDistance ?: ProjectionConfig.DEFAULT_MAX_RENDER_DISTANCE).coerceAtLeast(1.0)
        val maxDistSq = maxDist * maxDist

        // Special path: textured block models.
        if (s.visualMode == ProjectionVisualMode.BLOCK_MODEL) {
            // VBO cached path only for ALL (static requirement geometry). For MISMATCH_ONLY we need per-block filtering.
            if (s.renderMode == ProjectionRenderMode.ALL && OpenGlHelper.useVbo()) {
                val orientation = s.currentOrientation ?: s.lockedOrientation ?: StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)
                val slice = s.sliceCountOverride ?: s.defaultSliceCount
                val key = CacheKey(s.structureId, orientation, slice)
                val cache = ensureBlockModelRenderCacheLoaded(key, model)
                if (cache != null) {
                    renderBlockModelVboChunks(s, cache, camX, camY, camZ, maxDistSq)
                    return
                }
            }

            // Fallback: immediate-mode (no cache).
            renderBlockModels(s, model, world, camX, camY, camZ, maxDistSq)
            return
        }

        // Fast path: VBO cached ghost rendering.
        // Only enabled for renderMode=ALL because VBO is built from requirements (static), not dynamic statuses.
        if (s.renderMode == ProjectionRenderMode.ALL && s.visualMode != ProjectionVisualMode.OUTLINE && OpenGlHelper.useVbo()) {
            val orientation = s.currentOrientation ?: s.lockedOrientation ?: StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)
            val slice = s.sliceCountOverride ?: s.defaultSliceCount
            val key = CacheKey(s.structureId, orientation, slice)
            val cache = ensureRenderCacheLoaded(key, model)
            if (cache != null) {
                renderGhostVboChunks(s, cache, camX, camY, camZ, maxDistSq)

                // If BOTH, we still draw outlines in immediate mode (debug helper) after VBO pass.
                if (s.visualMode != ProjectionVisualMode.BOTH) {
                    return
                }
            }
        }

        // Render budgeted: iterate from a rolling cursor to avoid full scans every frame.
        // Also allow one-frame full render for small structures.
        val maxRenderThisFrame = if (s.entries.size <= ProjectionConfig.RENDER_ALL_IF_UNDER) Int.MAX_VALUE else ProjectionConfig.MAX_BLOCKS_RENDER_PER_FRAME
        val startCursor = s.renderCursor

        var rendered = 0
        var iter = 0
        val maxIter = s.entries.size.coerceAtLeast(1)

        prepareRenderState()

        val tessellator = Tessellator.getInstance()
        val buffer = tessellator.buffer

        // We emit geometry in a single draw call per primitive type.
        // For BOTH mode, we draw ghost first then outline.
        if (s.visualMode != ProjectionVisualMode.OUTLINE) {
            buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
        } else {
            buffer.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        }

        while (rendered < maxRenderThisFrame && iter < maxIter) {
            val idx = (startCursor + iter) % s.entries.size
            val entry = s.entries[idx]
            val status = s.statuses.getOrElse(idx) { Status.UNKNOWN }

            val shouldRender = when (s.renderMode) {
                ProjectionRenderMode.ALL -> true
                ProjectionRenderMode.MISMATCH_ONLY -> status != Status.MATCH
            }
            if (!shouldRender) {
                iter++
                continue
            }

            val pos = s.anchor.add(entry.rel)

            val dx = pos.x + 0.5 - camX
            val dy = pos.y + 0.5 - camY
            val dz = pos.z + 0.5 - camZ
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > maxDistSq) {
                iter++
                continue
            }

            val color = when (s.visualMode) {
                ProjectionVisualMode.OUTLINE -> {
                    // Debug view: keep status colors.
                    ProjectionColors.statusColor(status)
                }

                else -> {
                    // Main view: show expected blocks as neutral ghost cubes.
                    val base = ProjectionColors.ghostColor(entry.requirement)
                    when (status) {
                        Status.UNLOADED -> ProjectionColors.UNLOADED.copy(a = base.a)
                        else -> base
                    }
                }
            }
            val (r, g, b, a) = color

            val bb = AxisAlignedBB(pos).offset(-camX, -camY, -camZ)
            when (s.visualMode) {
                ProjectionVisualMode.OUTLINE -> putOutlinedAabb(buffer, bb.grow(0.002), r, g, b, a)
                ProjectionVisualMode.GHOST -> putGhostAabb(buffer, bb, r, g, b, a)
                ProjectionVisualMode.BOTH -> {
                    // In BOTH we first draw ghost (current buffer is QUADS) and later draw outline.
                    putGhostAabb(buffer, bb, r, g, b, a)
                }
                ProjectionVisualMode.BLOCK_MODEL -> {
                    // Should not happen because BLOCK_MODEL returns earlier.
                    // Fallback to ghost cube to stay safe.
                    putGhostAabb(buffer, bb, r, g, b, a)
                }
            }
            rendered++
            iter++
        }

        // Advance cursor after finishing current frame so we distribute work across frames.
        s.renderCursor = (startCursor + iter) % s.entries.size

        tessellator.draw()

        if (s.visualMode == ProjectionVisualMode.BOTH) {
            // Second pass: outlines.
            val buffer2 = Tessellator.getInstance().buffer
            buffer2.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)

            var rendered2 = 0
            var iter2 = 0
            while (rendered2 < maxRenderThisFrame && iter2 < maxIter) {
                val idx = (startCursor + iter2) % s.entries.size
                val entry = s.entries[idx]
                val status = s.statuses.getOrElse(idx) { Status.UNKNOWN }

                val shouldRender = when (s.renderMode) {
                    ProjectionRenderMode.ALL -> true
                    ProjectionRenderMode.MISMATCH_ONLY -> status != Status.MATCH
                }
                if (!shouldRender) {
                    iter2++
                    continue
                }

                val pos = s.anchor.add(entry.rel)
                val dx = pos.x + 0.5 - camX
                val dy = pos.y + 0.5 - camY
                val dz = pos.z + 0.5 - camZ
                val distSq = dx * dx + dy * dy + dz * dz
                if (distSq > maxDistSq) {
                    iter2++
                    continue
                }

                // For BOTH, outlines are just subtle helpers.
                // BOTH 模式下，描边仅作辅助，不再用“红框”主导视野。
                val (r, g, b, a) = when (status) {
                    Status.UNLOADED -> ProjectionColors.OUTLINE_UNLOADED
                    else -> ProjectionColors.OUTLINE_DEFAULT
                }

                val bb = AxisAlignedBB(pos).offset(-camX, -camY, -camZ).grow(0.002)
                putOutlinedAabb(buffer2, bb, r, g, b, a)

                rendered2++
                iter2++
            }

            Tessellator.getInstance().draw()
        }
        restoreRenderState()
    }

    private fun renderBlockModels(
        s: StructureProjectionSession,
        model: StructurePreviewModel,
        world: net.minecraft.world.World,
        camX: Double,
        camY: Double,
        camZ: Double,
        maxDistSq: Double
    ) {
        val mc = Minecraft.getMinecraft()

        // Desired overlay alpha for block-model preview.
        val overlayAlphaMul = ProjectionConfig.BLOCK_MODEL_OVERLAY_ALPHA

        // Render budgeted: similar to ghost path.
        val maxRenderThisFrame = if (s.entries.size <= ProjectionConfig.RENDER_ALL_IF_UNDER) Int.MAX_VALUE else ProjectionConfig.MAX_BLOCKS_RENDER_PER_FRAME
        val startCursor = s.renderCursor

        // State: textured, blended, depth-tested but do not write depth.
        GlStateManager.pushMatrix()
        GlStateManager.enableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        )
        GlStateManager.depthMask(false)
        GlStateManager.disableCull()
        RenderHelper.disableStandardItemLighting()

        // Reduce z-fighting when previewing blocks that already exist in-world.
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
        GL11.glPolygonOffset(ProjectionConfig.POLYGON_OFFSET_FACTOR, ProjectionConfig.POLYGON_OFFSET_UNITS)

        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)
        // Keep GL constant color opaque; actual translucency is applied to vertex colors.
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f)

        val dispatcher = mc.blockRendererDispatcher
        val shapes = dispatcher.blockModelShapes
        val renderer = dispatcher.blockModelRenderer

        val tess = Tessellator.getInstance()
        val buffer = tess.buffer
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK)
        buffer.setTranslation(-camX, -camY, -camZ)

        var rendered = 0
        var iter = 0
        val maxIter = s.entries.size.coerceAtLeast(1)

        while (rendered < maxRenderThisFrame && iter < maxIter) {
            val idx = (startCursor + iter) % s.entries.size
            val entry = s.entries[idx]

            val status = s.statuses.getOrElse(idx) { Status.UNKNOWN }
            val shouldRender = when (s.renderMode) {
                ProjectionRenderMode.ALL -> true
                ProjectionRenderMode.MISMATCH_ONLY -> status != Status.MATCH
            }
            if (!shouldRender) {
                iter++
                continue
            }

            val pos = s.anchor.add(entry.rel)
            val dx = pos.x + 0.5 - camX
            val dy = pos.y + 0.5 - camY
            val dz = pos.z + 0.5 - camZ
            val distSq = dx * dx + dy * dy + dz * dz
            if (distSq > maxDistSq) {
                iter++
                continue
            }

            // Try map requirement -> state.
            val state = stateFromRequirement(entry.requirement)
            if (state == null) {
                iter++
                continue
            }

            val baked = shapes.getModelForState(state)
            try {
                renderer.renderModel(world, baked, state, pos, buffer, false)
            } catch (_: Throwable) {
                // ignore broken models
            }

            rendered++
            iter++
        }

        s.renderCursor = (startCursor + iter) % s.entries.size

        buffer.setTranslation(0.0, 0.0, 0.0)

        // Submit manually so we can post-process vertex colors before drawing.
        buffer.finishDrawing()
        multiplyVertexAlpha(buffer, overlayAlphaMul)
        net.minecraft.client.renderer.WorldVertexBufferUploader().draw(buffer)

        GlStateManager.depthMask(true)
        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)
        GlStateManager.enableCull()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    private fun ensureBlockModelRenderCacheLoaded(key: CacheKey, model: StructurePreviewModel): BlockModelRenderCache? {
        blockModelRenderCache[key]?.let { return it }
        if (!OpenGlHelper.useVbo()) return null

        val built = buildBlockModelRenderCache(key, model)
        blockModelRenderCache[key] = built

        // LRU cap (separate from ghost cache)
        if (blockModelRenderCache.size > 8) {
            val it = blockModelRenderCache.entries.iterator()
            if (it.hasNext()) {
                val evicted = it.next().value
                it.remove()
                evicted.meshes.forEach { mesh ->
                    listOf(mesh.solid, mesh.cutoutMipped, mesh.cutout, mesh.translucent).forEach { vbo ->
                        if (vbo != null) {
                            try {
                                vbo.deleteGlBuffers()
                            } catch (_: Throwable) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }

        return built
    }

    private fun buildBlockModelRenderCache(key: CacheKey, model: StructurePreviewModel): BlockModelRenderCache {
        val mc = Minecraft.getMinecraft()
        val shapes = mc.blockRendererDispatcher.blockModelShapes
        val blockColors = mc.blockColors

        // Group by 16x16x16 relative chunks.
        val groups = LinkedHashMap<ChunkKey, MutableList<Entry>>()
        for ((rel, req) in model.blocks) {
            val ck = ChunkKey(floorDiv(rel.x, 16), floorDiv(rel.y, 16), floorDiv(rel.z, 16))
            groups.computeIfAbsent(ck) { mutableListOf() }.add(Entry(rel, req))
        }

        val overlayAlphaMul = ProjectionConfig.BLOCK_MODEL_OVERLAY_ALPHA
        val fullBright = ProjectionConfig.FULLBRIGHT_LIGHTMAP_UV
        val layers = arrayOf(
            BlockRenderLayer.SOLID,
            BlockRenderLayer.CUTOUT_MIPPED,
            BlockRenderLayer.CUTOUT,
            BlockRenderLayer.TRANSLUCENT
        )

        val meshes = ArrayList<BlockModelChunkMesh>(groups.size)
        for ((ck, entries) in groups) {
            // Compute bounds in relative space.
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var maxZ = Int.MIN_VALUE
            for (e in entries) {
                val p = e.rel
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.z < minZ) minZ = p.z
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
                if (p.z > maxZ) maxZ = p.z
            }

            val centerX = (minX.toDouble() + (maxX + 1).toDouble()) * 0.5
            val centerY = (minY.toDouble() + (maxY + 1).toDouble()) * 0.5
            val centerZ = (minZ.toDouble() + (maxZ + 1).toDouble()) * 0.5

            val rx = (maxX + 1 - minX) * 0.5
            val ry = (maxY + 1 - minY) * 0.5
            val rz = (maxZ + 1 - minZ) * 0.5
            val radiusSq = rx * rx + ry * ry + rz * rz

            // Build one VBO per render layer. Many models (e.g. MultiLayerModel) will filter quads by layer.
            val layerBuffers = EnumMap<BlockRenderLayer, BufferBuilder>(BlockRenderLayer::class.java)
            fun bufferFor(layer: BlockRenderLayer): BufferBuilder {
                return layerBuffers.getOrPut(layer) {
                    BufferBuilderPool.borrow(1 shl 19, tag = "WorldProjection.blockModel.$layer").also {
                        it.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK)
                    }
                }
            }

            try {
                for (e in entries) {
                    val state = stateFromRequirement(e.requirement) ?: continue
                    val baked = shapes.getModelForState(state)
                    val block = state.block

                    // Deterministic seed based on relative position (anchor-independent).
                    val seedPos = BlockPos(e.rel.x, e.rel.y, e.rel.z)
                    val baseSeed = MathHelper.getPositionRandom(seedPos)

                    for (layer in layers) {
                        if (!block.canRenderInLayer(state, layer)) continue
                        ForgeHooksClient.setRenderLayer(layer)
                        val buf = bufferFor(layer)

                        emitBakedQuads(buf, baked.getQuads(state, null, baseSeed), state, blockColors, fullBright, e.rel)
                        for (face in EnumFacing.values()) {
                            val seed = baseSeed + face.index.toLong() * 1315423911L
                            emitBakedQuads(buf, baked.getQuads(state, face, seed), state, blockColors, fullBright, e.rel)
                        }
                    }
                }
            } finally {
                ForgeHooksClient.setRenderLayer(null)
            }

            fun uploadLayer(layer: BlockRenderLayer): VertexBuffer? {
                val buf = layerBuffers[layer] ?: return null
                if (buf.vertexCount <= 0) {
                    BufferBuilderPool.recycle(buf)
                    return null
                }
                buf.finishDrawing()
                multiplyVertexAlpha(buf, overlayAlphaMul)
                val vbo = VertexBuffer(DefaultVertexFormats.BLOCK)
                vbo.bufferData(buf.byteBuffer)
                BufferBuilderPool.recycle(buf)
                return vbo
            }

            val solid = uploadLayer(BlockRenderLayer.SOLID)
            val cutoutMipped = uploadLayer(BlockRenderLayer.CUTOUT_MIPPED)
            val cutout = uploadLayer(BlockRenderLayer.CUTOUT)
            val translucent = uploadLayer(BlockRenderLayer.TRANSLUCENT)

            meshes.add(
                BlockModelChunkMesh(
                    key = ck,
                    centerX = centerX,
                    centerY = centerY,
                    centerZ = centerZ,
                    radiusSq = radiusSq,
                    solid = solid,
                    cutoutMipped = cutoutMipped,
                    cutout = cutout,
                    translucent = translucent,
                    approxBlockCount = entries.size
                )
            )
        }

        return BlockModelRenderCache(key, meshes)
    }

    private fun emitBakedQuads(
        buffer: BufferBuilder,
        quads: List<BakedQuad>,
        state: IBlockState,
        blockColors: net.minecraft.client.renderer.color.BlockColors,
        fullBright: Int,
        rel: BlockPos
    ) {
        if (quads.isEmpty()) return

        for (q in quads) {
            buffer.addVertexData(q.vertexData)

            // Tint if needed (fallback colors when no world/pos).
            if (q.hasTintIndex()) {
                val tint = try {
                    blockColors.colorMultiplier(state, null, null, q.tintIndex)
                } catch (_: Throwable) {
                    -1
                }
                if (tint != -1) {
                    val r = ((tint shr 16) and 0xFF) / 255.0f
                    val g = ((tint shr 8) and 0xFF) / 255.0f
                    val b = (tint and 0xFF) / 255.0f
                    buffer.putColorMultiplier(r, g, b, 4)
                    buffer.putColorMultiplier(r, g, b, 3)
                    buffer.putColorMultiplier(r, g, b, 2)
                    buffer.putColorMultiplier(r, g, b, 1)
                }
            }

            // Force fullbright (lightmap UV2 packed into int)
            buffer.putBrightness4(fullBright, fullBright, fullBright, fullBright)

            // Translate quad to its block position.
            buffer.putPosition(rel.x.toDouble(), rel.y.toDouble(), rel.z.toDouble())
        }
    }

    private fun renderBlockModelVboChunks(
        s: StructureProjectionSession,
        cache: BlockModelRenderCache,
        camX: Double,
        camY: Double,
        camZ: Double,
        maxDistSq: Double
    ) {
        if (cache.meshes.isEmpty()) return

        val mc = Minecraft.getMinecraft()

        GlStateManager.pushMatrix()
        GlStateManager.enableTexture2D()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        )
        GlStateManager.depthMask(false)
        GlStateManager.disableCull()
        RenderHelper.disableStandardItemLighting()

        // Reduce z-fighting (preview overlay vs existing blocks).
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
        GL11.glPolygonOffset(-1.0f, -10.0f)

        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)

        // Move to world position.
        GlStateManager.translate(
            (s.anchor.x.toDouble() - camX),
            (s.anchor.y.toDouble() - camY),
            (s.anchor.z.toDouble() - camZ)
        )

        // Enable required client states for DefaultVertexFormats.BLOCK.
        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY)

        // TexCoord 0
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit)
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)

        // TexCoord 1 (lightmap)
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit)
        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY)

        // Restore active tex unit.
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit)

        fun drawVbo(vbo: VertexBuffer) {
            vbo.bindBuffer()
            // DefaultVertexFormats.BLOCK layout:
            // pos(3f)=0..11, color(4ub)=12..15, uv0(2f)=16..23, uv2(2s)=24..27 ; stride=28
            GL11.glVertexPointer(3, GL11.GL_FLOAT, 28, 0L)
            GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12L)

            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit)
            GL11.glTexCoordPointer(2, GL11.GL_FLOAT, 28, 16L)

            OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit)
            GL11.glTexCoordPointer(2, GL11.GL_SHORT, 28, 24L)

            OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit)
            vbo.drawArrays(GL11.GL_QUADS)
            vbo.unbindBuffer()
        }

        fun meshDistSq(m: BlockModelChunkMesh): Double {
            val worldCenterX = s.anchor.x + m.centerX
            val worldCenterY = s.anchor.y + m.centerY
            val worldCenterZ = s.anchor.z + m.centerZ
            val dx = worldCenterX - camX
            val dy = worldCenterY - camY
            val dz = worldCenterZ - camZ
            return dx * dx + dy * dy + dz * dz
        }

        val meshes = cache.meshes
        val start = s.chunkRenderCursor
        val maxChunksThisFrame = if (meshes.size <= ProjectionConfig.RENDER_ALL_CHUNKS_IF_UNDER) Int.MAX_VALUE else ProjectionConfig.MAX_CHUNKS_RENDER_PER_FRAME

        // First pass: select visible chunks in a single scan (budgeted).
        // We include a chunk if it has any non-translucent layer VBO or a translucent VBO.
        val selected = ArrayList<BlockModelChunkMesh>(minOf(64, maxChunksThisFrame))
        var iter = 0
        while (selected.size < maxChunksThisFrame && iter < meshes.size) {
            val idx = (start + iter) % meshes.size
            val m = meshes[idx]

            val hasAny = (m.solid != null) || (m.cutoutMipped != null) || (m.cutout != null) || (m.translucent != null)
            if (hasAny) {
                val distSq = meshDistSq(m)
                if (distSq <= maxDistSq + m.radiusSq) {
                    selected.add(m)
                }
            }

            iter++
        }

        // Advance cursor once per frame for VBO path.
        s.chunkRenderCursor = (start + iter) % meshes.size

        // Render order similar to vanilla chunk rendering.
        // SOLID / CUTOUT_MIPPED / CUTOUT: no sorting.
        GlStateManager.enableAlpha()
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f)

        for (m in selected) {
            val vbo = m.solid ?: continue
            drawVbo(vbo)
        }

        for (m in selected) {
            val vbo = m.cutoutMipped ?: continue
            drawVbo(vbo)
        }

        for (m in selected) {
            val vbo = m.cutout ?: continue
            drawVbo(vbo)
        }

        // TRANSLUCENT: back-to-front sorting (chunk granularity) within selected set.
        val translucentMeshes = ArrayList<Pair<BlockModelChunkMesh, Double>>()
        for (m in selected) {
            if (m.translucent != null) {
                translucentMeshes.add(m to meshDistSq(m))
            }
        }

        if (translucentMeshes.isNotEmpty()) {
            translucentMeshes.sortByDescending { it.second }
            for ((m, _) in translucentMeshes) {
                val vbo = m.translucent ?: continue
                drawVbo(vbo)
            }
        }

        // Disable client states.
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.lightmapTexUnit)
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)
        OpenGlHelper.setClientActiveTexture(OpenGlHelper.defaultTexUnit)
        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY)

        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY)

        GL11.glDisable(GL11.GL_POLYGON_OFFSET_FILL)
        GlStateManager.depthMask(true)
        GlStateManager.enableCull()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    /**
     * Multiply the per-vertex alpha channel in the current [buffer].
     *
     * Needed because DefaultVertexFormats.BLOCK includes vertex color; GL constant color alpha is ignored.
     */
    private fun multiplyVertexAlpha(buffer: BufferBuilder, alphaMul: Float) {
        val mul = alphaMul.coerceIn(0.0f, 1.0f)
        if (mul >= 0.999f) return

        val format = buffer.vertexFormat
        if (!format.hasColor()) return

        val stride = format.size
        val colorOffset = format.colorOffset
        if (stride <= 0 || colorOffset < 0) return

        val bb = buffer.byteBuffer
        val vertexCount = buffer.vertexCount
        if (vertexCount <= 0) return

        // BufferBuilder stores colors as 4 bytes; alpha location depends on native endianness.
        val alphaByteOffset = if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) 3 else 0

        // NOTE: byteBuffer limit is already set by finishDrawing(). Use absolute get/put to avoid changing position.
        for (i in 0 until vertexCount) {
            val idx = i * stride + colorOffset + alphaByteOffset
            if (idx < 0 || idx >= bb.limit()) continue
            val oldA = bb.get(idx).toInt() and 0xFF
            val newA = (oldA.toFloat() * mul).toInt().coerceIn(0, 255)
            bb.put(idx, newA.toByte())
        }
    }

    private fun stateFromRequirement(req: github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement): IBlockState? {
        if (req !is ExactBlockStateRequirement) return null
        val block = Block.REGISTRY.getObject(req.blockId)
        if (block == Blocks.AIR) return null

        @Suppress("DEPRECATION")
        var state = block.getStateFromMeta(req.meta)

        if (req.properties.isNotEmpty()) {
            for ((k, v) in req.properties) {
                val prop = state.propertyKeys.firstOrNull { it.name == k } ?: continue
                try {
                    @Suppress("UNCHECKED_CAST")
                    val p = prop as IProperty<Comparable<Any>>
                    val parsed = p.parseValue(v)
                    if (parsed.isPresent) {
                        @Suppress("UNCHECKED_CAST")
                        state = state.withProperty(p as IProperty<Comparable<Any>>, parsed.get() as Comparable<Any>)
                    }
                } catch (_: Throwable) {
                    // ignore bad property values
                }
            }
        }

        return state
    }

    private fun ensureRenderCacheLoaded(key: CacheKey, model: StructurePreviewModel): RenderCache? {
        renderCache[key]?.let { return it }
        if (!OpenGlHelper.useVbo()) return null

        val built = buildRenderCache(key, model)
        renderCache[key] = built

        // LRU cap (separate from model cache)
        if (renderCache.size > 16) {
            val it = renderCache.entries.iterator()
            if (it.hasNext()) {
                val evicted = it.next().value
                it.remove()
                evicted.meshes.forEach { mesh ->
                    try {
                        mesh.vbo.deleteGlBuffers()
                    } catch (_: Throwable) {
                        // ignore
                    }
                }
            }
        }

        return built
    }

    private fun buildRenderCache(key: CacheKey, model: StructurePreviewModel): RenderCache {
        // Group by 16x16x16 relative chunks.
        val groups = LinkedHashMap<ChunkKey, MutableList<Entry>>()
        for ((rel, req) in model.blocks) {
            val ck = ChunkKey(floorDiv(rel.x, 16), floorDiv(rel.y, 16), floorDiv(rel.z, 16))
            groups.computeIfAbsent(ck) { mutableListOf() }.add(Entry(rel, req))
        }

        val meshes = ArrayList<ChunkMesh>(groups.size)
        for ((ck, entries) in groups) {
            // Compute bounds in relative space.
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var maxZ = Int.MIN_VALUE
            for (e in entries) {
                val p = e.rel
                if (p.x < minX) minX = p.x
                if (p.y < minY) minY = p.y
                if (p.z < minZ) minZ = p.z
                if (p.x > maxX) maxX = p.x
                if (p.y > maxY) maxY = p.y
                if (p.z > maxZ) maxZ = p.z
            }

            // Center of the chunk mesh in relative coordinates.
            val centerX = (minX.toDouble() + (maxX + 1).toDouble()) * 0.5
            val centerY = (minY.toDouble() + (maxY + 1).toDouble()) * 0.5
            val centerZ = (minZ.toDouble() + (maxZ + 1).toDouble()) * 0.5

            // Radius squared to approximate sphere for distance culling.
            val rx = (maxX + 1 - minX) * 0.5
            val ry = (maxY + 1 - minY) * 0.5
            val rz = (maxZ + 1 - minZ) * 0.5
            val radiusSq = rx * rx + ry * ry + rz * rz

            // Build VBO geometry.
            val buf = BufferBuilderPool.borrow(1 shl 18, tag = "WorldProjection.ghostChunk")
            buf.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
            for (e in entries) {
                val base = when (e.requirement) {
                    is ExactBlockStateRequirement -> ProjectionColors.GHOST_EXACT
                    else -> ProjectionColors.GHOST_DEFAULT
                }
                val bb = AxisAlignedBB(
                    e.rel.x.toDouble(), e.rel.y.toDouble(), e.rel.z.toDouble(),
                    (e.rel.x + 1).toDouble(), (e.rel.y + 1).toDouble(), (e.rel.z + 1).toDouble()
                )
                putGhostAabb(buf, bb, base.r, base.g, base.b, base.a)
            }
            buf.finishDrawing()

            val vbo = VertexBuffer(DefaultVertexFormats.POSITION_COLOR)
            vbo.bufferData(buf.byteBuffer)
            BufferBuilderPool.recycle(buf)

            meshes.add(
                ChunkMesh(
                    key = ck,
                    centerX = centerX,
                    centerY = centerY,
                    centerZ = centerZ,
                    radiusSq = radiusSq,
                    vbo = vbo,
                    approxBlockCount = entries.size
                )
            )
        }

        // Stable order: keep insertion order for cursor-based rendering.
        return RenderCache(key, meshes)
    }

    private fun renderGhostVboChunks(
        s: StructureProjectionSession,
        cache: RenderCache,
        camX: Double,
        camY: Double,
        camZ: Double,
        maxDistSq: Double
    ) {
        if (cache.meshes.isEmpty()) return

        prepareRenderState()
        GlStateManager.translate(
            (s.anchor.x.toDouble() - camX),
            (s.anchor.y.toDouble() - camY),
            (s.anchor.z.toDouble() - camZ)
        )

        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY)

        val meshes = cache.meshes
        val start = s.chunkRenderCursor
        val maxChunksThisFrame = if (meshes.size <= ProjectionConfig.RENDER_ALL_CHUNKS_IF_UNDER) Int.MAX_VALUE else ProjectionConfig.MAX_CHUNKS_RENDER_PER_FRAME

        var rendered = 0
        var iter = 0
        val maxIter = meshes.size

        while (rendered < maxChunksThisFrame && iter < maxIter) {
            val idx = (start + iter) % meshes.size
            val m = meshes[idx]

            // Chunk-level distance culling (sphere approx).
            // Camera in relative-space after translation => compare against -translation:
            // Equivalent: distance from (anchor + centerRel) to camera.
            val worldCenterX = s.anchor.x + m.centerX
            val worldCenterY = s.anchor.y + m.centerY
            val worldCenterZ = s.anchor.z + m.centerZ
            val dx = worldCenterX - camX
            val dy = worldCenterY - camY
            val dz = worldCenterZ - camZ
            val distSq = dx * dx + dy * dy + dz * dz

            // If center is too far plus radius, skip.
            if (distSq <= maxDistSq + m.radiusSq) {
                m.vbo.bindBuffer()
                // POSITION_COLOR = 3 floats (12 bytes) + 4 ubytes color (4 bytes) => stride 16.
                GL11.glVertexPointer(3, GL11.GL_FLOAT, 16, 0L)
                GL11.glColorPointer(4, GL11.GL_UNSIGNED_BYTE, 16, 12L)
                m.vbo.drawArrays(GL11.GL_QUADS)
                m.vbo.unbindBuffer()
                rendered++
            }

            iter++
        }

        s.chunkRenderCursor = (start + iter) % meshes.size

        // Disable client state after VBO rendering.
        // VBO 绘制后关闭 client state。
        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY)
        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY)

        restoreRenderState()
    }

    @SubscribeEvent
    fun onRenderOverlayText(event: RenderGameOverlayEvent.Text) {
        val s = session ?: return
        val mc = Minecraft.getMinecraft()
        mc.world ?: return
        mc.player ?: return

        val model = ensureModelLoaded(s) ?: return

        // Orientation HUD helper.
        ProjectionHudRenderer.renderOrientationStatus(event, s)

        // Even if we hit a real block that is NOT part of the preview, we still want to find
        // the nearest preview block along the ray BEFORE that hit.
        // 即使最终命中的是“非预览方块”，也要优先显示射线途中遇到的预览方块。
        val partial = try {
            mc.renderPartialTicks.toDouble()
        } catch (_: Throwable) {
            1.0
        }
        val player = mc.player ?: return
        val eye = player.getPositionEyes(partial.toFloat())
        val look = player.getLook(partial.toFloat())

        val maxDist = (s.maxRenderDistance ?: ProjectionConfig.DEFAULT_MAX_RENDER_DISTANCE).coerceAtLeast(1.0)
        val ray = mc.objectMouseOver
        val rayLimit = if (ray != null && ray.hitVec != null) {
            // cap by actual hit distance so we don't "see" preview blocks behind a solid target.
            // 限制在真实命中距离内，避免显示目标方块后面的预览。
            eye.distanceTo(ray.hitVec)
        } else {
            maxDist
        }
        val limit = minOf(maxDist, rayLimit)

        // First: try find preview block along ray within limit.
        val hit: BlockPos? = ProjectionRayMarcher.findPreviewBlockAlongRay(anchor = s.anchor, model = model, start = eye, dir = look, maxDist = limit)

        // Second: if none found, but we did hit a real block that *is* a preview block, allow it.
        // (This is mostly a safety net; the ray-march should already find it.)
        val finalHit = if (hit != null) {
            hit
        } else if (ray != null && ray.typeOfHit == RayTraceResult.Type.BLOCK) {
            val p = ray.blockPos
            val relTest = BlockPos(p.x - s.anchor.x, p.y - s.anchor.y, p.z - s.anchor.z)
            if (model.blocks.containsKey(relTest)) p else null
        } else {
            null
        }

        if (finalHit == null) return

        val rel = BlockPos(finalHit.x - s.anchor.x, finalHit.y - s.anchor.y, finalHit.z - s.anchor.z)
        val req = model.blocks[rel] ?: return

        ProjectionHudRenderer.renderBlockInfoOverlay(event, finalHit, req, s.anchor)
    }



    private fun ensureModelLoaded(s: StructureProjectionSession): StructurePreviewModel? {
        val orientation = s.currentOrientation
            ?: s.lockedOrientation
            ?: (s.frontOverride?.let { StructureOrientation(it, EnumFacing.UP) })
            ?: StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)
        val slice = s.sliceCountOverride ?: s.defaultSliceCount

        if (s.cachedModel != null && !s.modelDirty) {
            return s.cachedModel
        }

        val key = CacheKey(s.structureId, orientation, slice)
        val cached = modelCache[key]
        if (cached != null) {
            s.cachedModel = cached
            s.modelDirty = false
            s.invalidateEntries()
            return cached
        }

        val base = PrototypeMachineryAPI.structureRegistry.get(s.structureId) ?: return null
        val rotated = PrototypeMachineryAPI.structureRegistry.get(s.structureId, orientation, orientation.front) ?: base

        val model = StructurePreviewBuilder.build(
            rotated,
            StructurePreviewBuilder.Options(sliceCountSelector = { slice })
        )

        modelCache[key] = model
        // small LRU cap
        if (modelCache.size > 32) {
            val it = modelCache.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }

        s.cachedModel = model
        s.modelDirty = false
        s.invalidateEntries()
        return model
    }

    private fun resolveDesiredOrientation(s: StructureProjectionSession, player: net.minecraft.client.entity.EntityPlayerSP): StructureOrientation {
        // Follow mode: always clamp to player horizontal facing with UP top.
        if (s.followPlayerFacing) {
            return StructureOrientation(player.horizontalFacing, EnumFacing.UP)
        }

        // Locked mode: prefer explicit lockedOrientation.
        s.lockedOrientation?.let { return it }

        // Back-compat: if only frontOverride exists, assume top=UP.
        s.frontOverride?.let { return StructureOrientation(it, EnumFacing.UP) }

        return StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)
    }

    // (使用 ProjectionColors.Color 代替本地定义)

    private fun prepareRenderState() {
        GlStateManager.pushMatrix()
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.disableCull()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        )
        GlStateManager.glLineWidth(2.0f)
        GlStateManager.depthMask(false)
    }

    // Local extension for ProjectionColors.Color to support .copy(a = ...)
    private fun ProjectionColors.Color.copy(r: Float = this.r, g: Float = this.g, b: Float = this.b, a: Float = this.a): ProjectionColors.Color {
        return ProjectionColors.Color(r, g, b, a)
    }

    private fun restoreRenderState() {
        GlStateManager.depthMask(true)
        GlStateManager.enableCull()
        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    private fun putGhostAabb(buffer: BufferBuilder, bb: AxisAlignedBB, r: Float, g: Float, b: Float, a: Float) {
        // 6 faces, double-sided (cull disabled). Slightly shrink to avoid z-fighting with real blocks.
        val f = ProjectionConfig.GHOST_AABB_SHRINK
        val minX = bb.minX + f
        val minY = bb.minY + f
        val minZ = bb.minZ + f
        val maxX = bb.maxX - f
        val maxY = bb.maxY - f
        val maxZ = bb.maxZ - f

        // -Y
        quad(buffer, minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a)
        // +Y
        quad(buffer, minX, maxY, minZ, minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, r, g, b, a)
        // -Z
        quad(buffer, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ, maxX, minY, minZ, r, g, b, a)
        // +Z
        quad(buffer, minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a)
        // -X
        quad(buffer, minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a)
        // +X
        quad(buffer, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ, maxX, minY, maxZ, r, g, b, a)
    }

    private fun quad(
        buffer: BufferBuilder,
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        x3: Double, y3: Double, z3: Double,
        x4: Double, y4: Double, z4: Double,
        r: Float, g: Float, b: Float, a: Float
    ) {
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex()
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex()
        buffer.pos(x3, y3, z3).color(r, g, b, a).endVertex()
        buffer.pos(x4, y4, z4).color(r, g, b, a).endVertex()
    }

    private fun putOutlinedAabb(buffer: BufferBuilder, bb: AxisAlignedBB, r: Float, g: Float, b: Float, a: Float) {
        // 12 edges, rendered as independent line segments.
        // bottom rectangle
        line(buffer, bb.minX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.minZ, r, g, b, a)
        line(buffer, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.minY, bb.maxZ, r, g, b, a)
        line(buffer, bb.maxX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.maxZ, r, g, b, a)
        line(buffer, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.minY, bb.minZ, r, g, b, a)
        // top rectangle
        line(buffer, bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a)
        line(buffer, bb.maxX, bb.maxY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a)
        line(buffer, bb.maxX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a)
        line(buffer, bb.minX, bb.maxY, bb.maxZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a)
        // verticals
        line(buffer, bb.minX, bb.minY, bb.minZ, bb.minX, bb.maxY, bb.minZ, r, g, b, a)
        line(buffer, bb.maxX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.minZ, r, g, b, a)
        line(buffer, bb.maxX, bb.minY, bb.maxZ, bb.maxX, bb.maxY, bb.maxZ, r, g, b, a)
        line(buffer, bb.minX, bb.minY, bb.maxZ, bb.minX, bb.maxY, bb.maxZ, r, g, b, a)
    }

    private fun line(buffer: BufferBuilder, x1: Double, y1: Double, z1: Double, x2: Double, y2: Double, z2: Double, r: Float, g: Float, b: Float, a: Float) {
        buffer.pos(x1, y1, z1).color(r, g, b, a).endVertex()
        buffer.pos(x2, y2, z2).color(r, g, b, a).endVertex()
    }
}
