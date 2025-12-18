package github.kasuminova.prototypemachinery.client.preview.ui.widget

import com.cleanroommc.modularui.api.UpOrDown
import com.cleanroommc.modularui.api.widget.IGuiAction
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext
import com.cleanroommc.modularui.theme.WidgetThemeEntry
import com.cleanroommc.modularui.widget.Widget
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import github.kasuminova.prototypemachinery.client.preview.ProjectionConfig
import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.BlockRendererDispatcher
import net.minecraft.client.renderer.BufferBuilder
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.block.model.BakedQuad
import net.minecraft.client.renderer.texture.TextureMap
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.client.renderer.vertex.VertexBuffer
import net.minecraft.client.renderer.vertex.VertexFormat
import net.minecraft.init.Biomes
import net.minecraft.init.Blocks
import net.minecraft.tileentity.TileEntity
import net.minecraft.util.BlockRenderLayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos
import net.minecraft.world.IBlockAccess
import net.minecraft.world.WorldType
import net.minecraft.world.biome.Biome
import net.minecraftforge.client.ForgeHooksClient
import org.lwjgl.opengl.GL11
import org.lwjgl.util.glu.GLU
import java.nio.ByteOrder
import java.util.EnumMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * 3D structure preview widget.
 *
 * Design goals:
 * - Host-agnostic & JEI-friendly: does NOT require a world.
 * - Low coupling: rendering is contained inside this widget.
 * - Interactive: drag to rotate, scroll to zoom.
 *
 * Rendering strategy (initial milestone):
 * - Draw boundary voxels as colored wireframe cubes.
 * - Optional per-block status coloring via [statusProvider].
 */
internal class StructurePreview3DWidget(
    private val model: StructurePreviewModel,
    private val statusProvider: (() -> Map<BlockPos, StructurePreviewEntryStatus>)? = null,
    /** When true, only render cubes whose status is not MATCH. */
    private val issuesOnlyProvider: (() -> Boolean)? = null,
    /** When true, only render cubes at [sliceYProvider] (relative Y within bounds). */
    private val sliceModeProvider: (() -> Boolean)? = null,
    /** Relative Y (0..sizeY-1) used when [sliceModeProvider] is true. */
    private val sliceYProvider: (() -> Int)? = null,
    /** When true, slowly rotates the camera automatically. */
    private val autoRotateProvider: (() -> Boolean)? = null,
    /** When false, hide wireframe overlays (axes + cube lines) to better see block model rendering. */
    private val wireframeProvider: (() -> Boolean)? = null
) : Widget<StructurePreview3DWidget>() {

    private data class Cube(val x: Int, val y: Int, val z: Int, val keyHash: Int)

    private data class BlockEntry(
        val relX: Int,
        val relY: Int,
        val relZ: Int,
        val requirement: BlockRequirement
    ) {
        val relPos: BlockPos get() = BlockPos(relX, relY, relZ)
    }

    private data class ChunkKey(val cx: Int, val cy: Int, val cz: Int)

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

    private val cubes: List<Cube>
    private val blockEntries: List<BlockEntry>
    private val min: BlockPos = model.bounds.min
    private val maxB: BlockPos = model.bounds.max

    /** Full block state map (structure-local coords) for adjacency queries. */
    private val allBlockStates: Map<BlockPos, IBlockState>

    /** Dummy access providing neighbor states + fullbright, for renderBlock face culling. */
    private val blockAccess: IBlockAccess

    // ===== Block model VBO cache (built incrementally) =====

    private val blockModelGroups: List<Pair<ChunkKey, List<BlockEntry>>>
    private val builtBlockModelMeshes: MutableList<BlockModelChunkMesh> = ArrayList()
    private var blockModelBuildCursor: Int = 0
    private var blockModelRenderCursor: Int = 0

    /** Whether we have attempted to build block model meshes (success or not). */
    private var blockModelBuildStarted: Boolean = false

    /** VBO availability snapshot; if false, we skip the VBO path entirely. */
    private val vboAvailable: Boolean = OpenGlHelper.useVbo()

    /** Small LRU to avoid re-parsing the same requirement into state repeatedly during build. */
    private val reqStateCache: LinkedHashMap<String, IBlockState?> = LinkedHashMap(128, 0.75f, true)

    /** Target camera state written by input handlers. */
    private var yawTargetDeg: Float = 35f
    private var pitchTargetDeg: Float = 25f
    private var zoomTarget: Float = 1.0f

    /** Smoothed camera state used for rendering (updated every frame in draw()). */
    private var yawSmoothDeg: Float = yawTargetDeg
    private var pitchSmoothDeg: Float = pitchTargetDeg
    private var zoomSmooth: Float = zoomTarget

    /** Last frame timestamp for dt-based interpolation (ms). */
    private var lastFrameTimeMs: Long = -1L

    private var dragging: Boolean = false
    private var dragButton: Int = -1
    private var lastDragAbsX: Int = 0
    private var lastDragAbsY: Int = 0

    init {
        cubes = buildBoundaryCubes(model)
        blockEntries = buildBoundaryBlockEntries(model)
        blockModelGroups = groupByChunk(blockEntries)

        allBlockStates = buildAllBlockStates(model)
        blockAccess = StructureBlockAccess(allBlockStates, ProjectionConfig.FULLBRIGHT_LIGHTMAP_UV)
    }

    fun resetView() {
        yawTargetDeg = 35f
        pitchTargetDeg = 25f
        zoomTarget = 1.0f
        yawSmoothDeg = yawTargetDeg
        pitchSmoothDeg = pitchTargetDeg
        zoomSmooth = zoomTarget
        lastFrameTimeMs = -1L
        dragging = false
        dragButton = -1
    }

    override fun onUpdate() {
        super.onUpdate()
        if (autoRotateProvider?.invoke() == true) {
            // Write to target so auto-rotate also benefits from smoothing.
            yawTargetDeg += 2.0f
        }
    }

    override fun dispose() {
        // Release VBOs to avoid leaking GPU memory when UI closes.
        for (m in builtBlockModelMeshes) {
            try {
                m.solid?.deleteGlBuffers()
            } catch (_: Throwable) {
                // ignore
            }
            try {
                m.cutoutMipped?.deleteGlBuffers()
            } catch (_: Throwable) {
                // ignore
            }
            try {
                m.cutout?.deleteGlBuffers()
            } catch (_: Throwable) {
                // ignore
            }
            try {
                m.translucent?.deleteGlBuffers()
            } catch (_: Throwable) {
                // ignore
            }
        }
        builtBlockModelMeshes.clear()
        super.dispose()
    }

    override fun afterInit() {
        super.afterInit()

        // Mouse press: start dragging when clicked inside widget.
        listenGuiAction(object : IGuiAction.MousePressed {
            override fun press(mouseButton: Int): Boolean {
                val ctx = context
                if (!ctx.isMouseAbove(this@StructurePreview3DWidget)) return false
                dragging = true
                dragButton = mouseButton
                lastDragAbsX = ctx.absMouseX
                lastDragAbsY = ctx.absMouseY
                return true
            }
        })

        // Mouse release: stop drag.
        listenGuiAction(object : IGuiAction.MouseReleased {
            override fun release(mouseButton: Int): Boolean {
                if (!dragging) return false
                if (mouseButton != dragButton) return false
                dragging = false
                dragButton = -1
                return true
            }
        })

        // Mouse drag: rotate camera.
        listenGuiAction(object : IGuiAction.MouseDrag {
            override fun drag(mouseButton: Int, timeSinceClick: Long): Boolean {
                if (!dragging) return false
                if (mouseButton != dragButton) return false

                val ctx = context
                val dx = ctx.absMouseX - lastDragAbsX
                val dy = ctx.absMouseY - lastDragAbsY

                // Avoid huge jumps on lost focus.
                if (abs(dx) > 200 || abs(dy) > 200) {
                    lastDragAbsX = ctx.absMouseX
                    lastDragAbsY = ctx.absMouseY
                    return true
                }

                // Drive targets; rendering uses smoothed values.
                yawTargetDeg += dx * 0.65f
                pitchTargetDeg += dy * 0.65f
                if (pitchTargetDeg < -89f) pitchTargetDeg = -89f
                if (pitchTargetDeg > 89f) pitchTargetDeg = 89f

                lastDragAbsX = ctx.absMouseX
                lastDragAbsY = ctx.absMouseY
                return true
            }
        })

        // Scroll: zoom.
        listenGuiAction(object : IGuiAction.MouseScroll {
            override fun scroll(direction: UpOrDown, amount: Int): Boolean {
                val ctx = context
                if (!ctx.isMouseAbove(this@StructurePreview3DWidget)) return false

                val step = 1.0f + (amount.coerceAtMost(10) * 0.08f)
                zoomTarget = when (direction) {
                    UpOrDown.UP -> (zoomTarget / step)
                    UpOrDown.DOWN -> (zoomTarget * step)
                }
                if (zoomTarget < 0.25f) zoomTarget = 0.25f
                if (zoomTarget > 8.0f) zoomTarget = 8.0f
                return true
            }
        })
    }

    override fun draw(context: ModularGuiContext, widgetTheme: WidgetThemeEntry<*>) {
        // Avoid any heavy work if not visible.
        if (!isEnabled || !areAncestorsEnabled()) return
        if (area.w() <= 2 || area.h() <= 2) return

        // dt-based smoothing for camera parameters (FPS-independent).
        // Use exponential smoothing: alpha = 1 - exp(-dt / tau).
        // Smaller tau => snappier; bigger tau => smoother.
        val nowMs = Minecraft.getSystemTime()
        val dtSec = if (lastFrameTimeMs < 0L) 0.0 else ((nowMs - lastFrameTimeMs).toDouble() / 1000.0)
        lastFrameTimeMs = nowMs

        // Avoid giant jumps if the UI was paused/unfocused.
        val clampedDt = min(dtSec, 0.25)

        val tau = if (dragging) 0.06 else 0.12
        val alpha = (1.0 - exp(-clampedDt / tau)).toFloat()

        yawSmoothDeg += (yawTargetDeg - yawSmoothDeg) * alpha
        pitchSmoothDeg += (pitchTargetDeg - pitchSmoothDeg) * alpha
        zoomSmooth += (zoomTarget - zoomSmooth) * alpha

        val mc = Minecraft.getMinecraft()
        val scaled = ScaledResolution(mc)
        val scale = scaled.scaleFactor

        val scX = area.x() * scale
        val scY = mc.displayHeight - (area.y() + area.h()) * scale
        val scW = area.w() * scale
        val scH = area.h() * scale

        GlStateManager.pushMatrix()
        GlStateManager.pushAttrib()

        // Clip to widget area.
        GL11.glEnable(GL11.GL_SCISSOR_TEST)
        GL11.glScissor(scX, scY, scW, scH)

        // IMPORTANT: align viewport with widget area.
        // Without setting viewport, projection aspect is computed from the widget,
        // but NDC is still mapped to the full-screen viewport, which causes perspective
        // distortion and depth oddities while rotating.
        GL11.glViewport(scX, scY, scW, scH)

        // Setup 3D.
        GlStateManager.enableBlend()
        GlStateManager.enableDepth()
        GlStateManager.depthFunc(GL11.GL_LEQUAL)
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, 1, 0)

        // Clear depth only (do not clear color of whole screen!).
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT)

        // Projection matrix.
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()
        val aspect = area.w().toFloat() / area.h().toFloat()
        GLU.gluPerspective(45f, aspect, 0.1f, 2000f)

        // ModelView matrix.
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)
        GlStateManager.pushMatrix()
        GlStateManager.loadIdentity()

        val sizeX = (maxB.x - min.x + 1).coerceAtLeast(1)
        val sizeY = (maxB.y - min.y + 1).coerceAtLeast(1)
        val sizeZ = (maxB.z - min.z + 1).coerceAtLeast(1)
        val maxDim = max(sizeX, max(sizeY, sizeZ)).toFloat()

        // Place camera.
        val baseDistance = maxDim * 2.6f
        val cameraDist = baseDistance * zoomSmooth
        GlStateManager.translate(0f, 0f, -cameraDist)
        GlStateManager.rotate(pitchSmoothDeg, 1f, 0f, 0f)
        GlStateManager.rotate(yawSmoothDeg, 0f, 1f, 0f)

        // Center structure around origin.
        val cx = sizeX * 0.5f
        val cy = sizeY * 0.5f
        val cz = sizeZ * 0.5f
        GlStateManager.translate(-cx, -cy, -cz)

        // Textured block models (performance path: VBO, built incrementally).
        drawBlockModels(context)

        // Wireframe overlays.
        GlStateManager.disableTexture2D()

        if (wireframeProvider?.invoke() != false) {
            // Draw axes (subtle).
            drawAxes(sizeX.toFloat(), sizeY.toFloat(), sizeZ.toFloat())

            // Draw cubes.
            drawCubes(context)
        }

        // Restore matrices.
        GlStateManager.popMatrix() // MODELVIEW
        GlStateManager.matrixMode(GL11.GL_PROJECTION)
        GlStateManager.popMatrix()
        GlStateManager.matrixMode(GL11.GL_MODELVIEW)

        // Restore full-screen viewport for the rest of the GUI.
        GL11.glViewport(0, 0, mc.displayWidth, mc.displayHeight)

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GlStateManager.popAttrib()
        GlStateManager.popMatrix()

        // Let base class handle hover timers etc.
        super.draw(context, widgetTheme)
    }

    private fun drawBlockModels(context: ModularGuiContext) {
        if (!vboAvailable) return
        if (blockEntries.isEmpty()) return

        // Slice mode is primarily for debugging layer-by-layer; keeping VBOs static avoids costly rebuilds.
        // We therefore skip block models when slice mode is enabled and rely on wireframe overlays.
        if (sliceModeProvider?.invoke() == true) return

        // Start/continue incremental building.
        if (!blockModelBuildStarted) {
            blockModelBuildStarted = true
        }
        buildSomeBlockModelMeshesPerFrame()

        if (builtBlockModelMeshes.isEmpty()) return

        val mc = Minecraft.getMinecraft()

        GlStateManager.pushMatrix()
        GlStateManager.enableTexture2D()
        GlStateManager.enableDepth()
        GlStateManager.enableCull()
        RenderHelper.disableStandardItemLighting()

        // Match world-like rendering: SOLID/CUTOUT write depth and don't use blending.
        // TRANSLUCENT is drawn last with blending and without depth writes.
        GlStateManager.disableBlend()
        GlStateManager.depthMask(true)
        GlStateManager.color(1f, 1f, 1f, 1f)

        // Reduce z-fighting between block models and wireframe overlay.
        GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL)
        GL11.glPolygonOffset(ProjectionConfig.POLYGON_OFFSET_FACTOR, ProjectionConfig.POLYGON_OFFSET_UNITS)

        mc.renderEngine.bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE)

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

        // Approx camera position (in structure local coords) for translucent sorting.
        val sizeX = (maxB.x - min.x + 1).coerceAtLeast(1)
        val sizeY = (maxB.y - min.y + 1).coerceAtLeast(1)
        val sizeZ = (maxB.z - min.z + 1).coerceAtLeast(1)
        val cx = sizeX * 0.5
        val cy = sizeY * 0.5
        val cz = sizeZ * 0.5

        val maxDim = max(sizeX, max(sizeY, sizeZ)).toDouble()
        val baseDistance = maxDim * 2.6
        val cameraDist = baseDistance * zoomSmooth.toDouble()

        val yawRad = Math.toRadians(yawSmoothDeg.toDouble())
        val pitchRad = Math.toRadians(pitchSmoothDeg.toDouble())
        val cosPitch = cos(pitchRad)
        val sinPitch = sin(pitchRad)
        val sinYaw = sin(yawRad)
        val cosYaw = cos(yawRad)

        val camX = cx - cameraDist * cosPitch * sinYaw
        val camY = cy - cameraDist * sinPitch
        val camZ = cz + cameraDist * cosPitch * cosYaw

        fun meshDistSq(m: BlockModelChunkMesh): Double {
            val dx = m.centerX - camX
            val dy = m.centerY - camY
            val dz = m.centerZ - camZ
            return dx * dx + dy * dy + dz * dz
        }

        val meshes = builtBlockModelMeshes
        val start = blockModelRenderCursor
        val maxChunksThisFrame = if (meshes.size <= ProjectionConfig.RENDER_ALL_CHUNKS_IF_UNDER) Int.MAX_VALUE else ProjectionConfig.MAX_CHUNKS_RENDER_PER_FRAME

        // Budgeted selection: iterate a moving window each frame.
        val selected = ArrayList<BlockModelChunkMesh>(minOf(64, maxChunksThisFrame))
        var iter = 0
        while (selected.size < maxChunksThisFrame && iter < meshes.size) {
            val idx = (start + iter) % meshes.size
            val m = meshes[idx]
            val hasAny = (m.solid != null) || (m.cutoutMipped != null) || (m.cutout != null) || (m.translucent != null)
            if (hasAny) selected.add(m)
            iter++
        }
        blockModelRenderCursor = if (meshes.isEmpty()) 0 else ((start + iter) % meshes.size)

        // Render order similar to vanilla chunk rendering.
        // SOLID / CUTOUT_MIPPED / CUTOUT: no sorting.
        GlStateManager.disableBlend()
        GlStateManager.depthMask(true)
        GlStateManager.enableAlpha()
        GlStateManager.alphaFunc(GL11.GL_GREATER, 0.1f)
        GlStateManager.shadeModel(GL11.GL_FLAT)

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

        // TRANSLUCENT: back-to-front sorting (chunk granularity).
        GlStateManager.tryBlendFuncSeparate(
            GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
            GlStateManager.SourceFactor.ONE,
            GlStateManager.DestFactor.ZERO
        )
        GlStateManager.enableBlend()
        GlStateManager.depthMask(false)
        GlStateManager.shadeModel(GL11.GL_SMOOTH)

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
        GlStateManager.disableBlend()
        GlStateManager.disableAlpha()
        GlStateManager.enableCull()
        GlStateManager.popMatrix()
    }

    private fun buildSomeBlockModelMeshesPerFrame() {
        if (!vboAvailable) return
        if (blockModelBuildCursor >= blockModelGroups.size) return

        // Budget: build a small number of chunk meshes per frame to avoid UI hitching.
        val maxBuildThisFrame = if (blockModelGroups.size <= 12) Int.MAX_VALUE else 2

        val mc = Minecraft.getMinecraft()
        val brd: BlockRendererDispatcher = mc.blockRendererDispatcher

        val layers = arrayOf(
            BlockRenderLayer.SOLID,
            BlockRenderLayer.CUTOUT_MIPPED,
            BlockRenderLayer.CUTOUT,
            BlockRenderLayer.TRANSLUCENT
        )

        var built = 0
        while (built < maxBuildThisFrame && blockModelBuildCursor < blockModelGroups.size) {
            val (ck, entries) = blockModelGroups[blockModelBuildCursor]
            blockModelBuildCursor++

            // Compute bounds in structure-local space.
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            var maxZ = Int.MIN_VALUE
            for (e in entries) {
                if (e.relX < minX) minX = e.relX
                if (e.relY < minY) minY = e.relY
                if (e.relZ < minZ) minZ = e.relZ
                if (e.relX > maxX) maxX = e.relX
                if (e.relY > maxY) maxY = e.relY
                if (e.relZ > maxZ) maxZ = e.relZ
            }

            val centerX = (minX.toDouble() + (maxX + 1).toDouble()) * 0.5
            val centerY = (minY.toDouble() + (maxY + 1).toDouble()) * 0.5
            val centerZ = (minZ.toDouble() + (maxZ + 1).toDouble()) * 0.5

            val rx = (maxX + 1 - minX) * 0.5
            val ry = (maxY + 1 - minY) * 0.5
            val rz = (maxZ + 1 - minZ) * 0.5
            val radiusSq = rx * rx + ry * ry + rz * rz

            // Build one VBO per render layer.
            val layerBuffers = EnumMap<BlockRenderLayer, BufferBuilder>(BlockRenderLayer::class.java)
            fun bufferFor(layer: BlockRenderLayer): BufferBuilder {
                return layerBuffers.getOrPut(layer) {
                    BufferBuilder(1 shl 19).also { it.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK) }
                }
            }

            try {
                for (e in entries) {
                    // Use the full state map so neighbor face-culling sees the complete structure.
                    val rawState = allBlockStates[e.relPos] ?: continue
                    val state = try {
                        rawState.block.getActualState(rawState, blockAccess, e.relPos)
                    } catch (_: Throwable) {
                        rawState
                    }
                    val block = state.block

                    for (layer in layers) {
                        if (!block.canRenderInLayer(state, layer)) continue
                        ForgeHooksClient.setRenderLayer(layer)
                        val buf = bufferFor(layer)

                        // Vanilla path handles ambient-occlusion & neighbor face culling.
                        brd.renderBlock(state, e.relPos, blockAccess, buf)
                    }
                }
            } finally {
                ForgeHooksClient.setRenderLayer(null)
            }

            fun uploadLayer(layer: BlockRenderLayer): VertexBuffer? {
                val buf = layerBuffers[layer] ?: return null
                if (buf.vertexCount <= 0) {
                    buf.reset()
                    return null
                }
                buf.finishDrawing()
                val vbo = VertexBuffer(DefaultVertexFormats.BLOCK)
                vbo.bufferData(buf.byteBuffer)
                buf.reset()
                return vbo
            }

            val solid = uploadLayer(BlockRenderLayer.SOLID)
            val cutoutMipped = uploadLayer(BlockRenderLayer.CUTOUT_MIPPED)
            val cutout = uploadLayer(BlockRenderLayer.CUTOUT)
            val translucent = uploadLayer(BlockRenderLayer.TRANSLUCENT)

            builtBlockModelMeshes.add(
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

            built++
        }
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

    /**
     * Multiply the per-vertex alpha channel in the current [buffer].
     *
     * Needed because DefaultVertexFormats.BLOCK includes vertex color; GL constant color alpha is ignored.
     */
    private fun multiplyVertexAlpha(buffer: BufferBuilder, alphaMul: Float) {
        val mul = alphaMul.coerceIn(0.0f, 1.0f)
        if (mul >= 0.999f) return

        val format: VertexFormat = buffer.vertexFormat
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

    private fun stateFromRequirementCached(req: BlockRequirement): IBlockState? {
        val key = try {
            req.stableKey()
        } catch (_: Throwable) {
            // Fallback: most requirements implement stableKey(); if not, use class name.
            req.javaClass.name
        }

        if (reqStateCache.containsKey(key)) return reqStateCache[key]

        val state = stateFromRequirement(req)
        reqStateCache[key] = state

        // Cap cache size.
        if (reqStateCache.size > 256) {
            val it = reqStateCache.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            }
        }

        return state
    }

    private fun stateFromRequirement(req: BlockRequirement): IBlockState? {
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

    private fun buildAllBlockStates(model: StructurePreviewModel): Map<BlockPos, IBlockState> {
        if (model.blocks.isEmpty()) return emptyMap()

        val out = HashMap<BlockPos, IBlockState>(model.blocks.size * 2)
        for ((pos, req) in model.blocks) {
            val rel = BlockPos(pos.x - min.x, pos.y - min.y, pos.z - min.z)
            val state = when (req) {
                is ExactBlockStateRequirement -> stateFromRequirement(req) ?: Blocks.AIR.defaultState
                else -> {
                    // Fallback for non-exact requirements: ensure neighbors exist so face culling works.
                    Blocks.STONE.defaultState
                }
            }
            out[rel] = state
        }
        return out
    }

    private class StructureBlockAccess(
        private val states: Map<BlockPos, IBlockState>,
        private val fullBright: Int
    ) : IBlockAccess {

        override fun getBlockState(pos: BlockPos): IBlockState {
            return states[pos] ?: Blocks.AIR.defaultState
        }

        override fun getTileEntity(pos: BlockPos): TileEntity? = null

        override fun getCombinedLight(pos: BlockPos, lightValue: Int): Int = fullBright

        override fun getBiome(pos: BlockPos): Biome = Biomes.PLAINS

        override fun getStrongPower(pos: BlockPos, direction: EnumFacing): Int = 0

        override fun getWorldType(): WorldType = WorldType.DEFAULT

        override fun isAirBlock(pos: BlockPos): Boolean {
            val s = getBlockState(pos)
            return s.material == Material.AIR || s.block === Blocks.AIR
        }

        override fun isSideSolid(pos: BlockPos, side: EnumFacing, _default: Boolean): Boolean {
            return getBlockState(pos).isSideSolid(this, pos, side)
        }
    }

    private fun drawAxes(x: Float, y: Float, z: Float) {
        val t = Tessellator.getInstance()
        val b = t.buffer
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)

        // X (red)
        b.pos(0.0, 0.0, 0.0).color(255, 80, 80, 120).endVertex()
        b.pos(x.toDouble(), 0.0, 0.0).color(255, 80, 80, 120).endVertex()

        // Y (green)
        b.pos(0.0, 0.0, 0.0).color(80, 255, 80, 120).endVertex()
        b.pos(0.0, y.toDouble(), 0.0).color(80, 255, 80, 120).endVertex()

        // Z (blue)
        b.pos(0.0, 0.0, 0.0).color(80, 80, 255, 120).endVertex()
        b.pos(0.0, 0.0, z.toDouble()).color(80, 80, 255, 120).endVertex()

        t.draw()
    }

    private fun drawCubes(context: ModularGuiContext) {
        val statuses = statusProvider?.invoke() ?: emptyMap()

        val issuesOnly = issuesOnlyProvider?.invoke() == true
        val sliceMode = sliceModeProvider?.invoke() == true
        val sizeY = (maxB.y - min.y + 1).coerceAtLeast(1)
        val sliceY = (sliceYProvider?.invoke() ?: 0).coerceIn(0, sizeY - 1)

        val t = Tessellator.getInstance()
        val b = t.buffer
        b.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)

        for (c in cubes) {
            if (sliceMode && c.y != sliceY) continue
            val rel = BlockPos(c.x + min.x, c.y + min.y, c.z + min.z)
            val status = statuses[rel]
            if (issuesOnly && status == StructurePreviewEntryStatus.MATCH) continue
            val rgba = colorFor(status, c.keyHash)
            val r = (rgba shr 16) and 0xFF
            val g = (rgba shr 8) and 0xFF
            val bl = (rgba) and 0xFF
            val a = (rgba ushr 24) and 0xFF

            addWireCube(b, c.x.toDouble(), c.y.toDouble(), c.z.toDouble(), r, g, bl, a)
        }

        t.draw()

        // Slightly thicker outline is not available with tessellator easily; leave it thin for now.
    }

    private fun colorFor(status: StructurePreviewEntryStatus?, keyHash: Int): Int {
        return when (status) {
            StructurePreviewEntryStatus.MATCH -> 0xCC55FF55.toInt()
            StructurePreviewEntryStatus.MISSING -> 0xCCFF5555.toInt()
            StructurePreviewEntryStatus.WRONG -> 0xCCFFAA55.toInt()
            StructurePreviewEntryStatus.UNLOADED -> 0xCC55FFFF.toInt()
            StructurePreviewEntryStatus.UNKNOWN, null -> {
                // Stable pseudo-color by key hash (avoid everything looking the same in JEI mode).
                val h = keyHash
                val r = 120 + (h and 0x3F)
                val g = 120 + ((h shr 6) and 0x3F)
                val b = 120 + ((h shr 12) and 0x3F)
                (0xAA shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
    }

    private fun addWireCube(
        b: net.minecraft.client.renderer.BufferBuilder,
        x: Double,
        y: Double,
        z: Double,
        r: Int,
        g: Int,
        bl: Int,
        a: Int
    ) {
        val x0 = x
        val y0 = y
        val z0 = z
        val x1 = x + 1.0
        val y1 = y + 1.0
        val z1 = z + 1.0

        // 12 edges.
        line(b, x0, y0, z0, x1, y0, z0, r, g, bl, a)
        line(b, x0, y1, z0, x1, y1, z0, r, g, bl, a)
        line(b, x0, y0, z1, x1, y0, z1, r, g, bl, a)
        line(b, x0, y1, z1, x1, y1, z1, r, g, bl, a)

        line(b, x0, y0, z0, x0, y1, z0, r, g, bl, a)
        line(b, x1, y0, z0, x1, y1, z0, r, g, bl, a)
        line(b, x0, y0, z1, x0, y1, z1, r, g, bl, a)
        line(b, x1, y0, z1, x1, y1, z1, r, g, bl, a)

        line(b, x0, y0, z0, x0, y0, z1, r, g, bl, a)
        line(b, x1, y0, z0, x1, y0, z1, r, g, bl, a)
        line(b, x0, y1, z0, x0, y1, z1, r, g, bl, a)
        line(b, x1, y1, z0, x1, y1, z1, r, g, bl, a)
    }

    private fun line(
        b: net.minecraft.client.renderer.BufferBuilder,
        x0: Double,
        y0: Double,
        z0: Double,
        x1: Double,
        y1: Double,
        z1: Double,
        r: Int,
        g: Int,
        bl: Int,
        a: Int
    ) {
        b.pos(x0, y0, z0).color(r, g, bl, a).endVertex()
        b.pos(x1, y1, z1).color(r, g, bl, a).endVertex()
    }

    private fun buildBoundaryCubes(model: StructurePreviewModel): List<Cube> {
        val blocks = model.blocks
        if (blocks.isEmpty()) return emptyList()

        val present = HashSet<BlockPos>(blocks.size * 2)
        for (p in blocks.keys) present.add(p)

        val out = ArrayList<Cube>(blocks.size)
        for ((pos, req) in blocks) {
            if (!isBoundary(pos, present)) continue
            out.add(
                Cube(
                    x = pos.x - min.x,
                    y = pos.y - min.y,
                    z = pos.z - min.z,
                    keyHash = req.stableKey().hashCode()
                )
            )
        }
        return out
    }

    private fun buildBoundaryBlockEntries(model: StructurePreviewModel): List<BlockEntry> {
        val blocks = model.blocks
        if (blocks.isEmpty()) return emptyList()

        val present = HashSet<BlockPos>(blocks.size * 2)
        for (p in blocks.keys) present.add(p)

        val out = ArrayList<BlockEntry>(blocks.size)
        for ((pos, req) in blocks) {
            if (!isBoundary(pos, present)) continue
            out.add(
                BlockEntry(
                    relX = pos.x - min.x,
                    relY = pos.y - min.y,
                    relZ = pos.z - min.z,
                    requirement = req
                )
            )
        }
        return out
    }

    private fun groupByChunk(entries: List<BlockEntry>): List<Pair<ChunkKey, List<BlockEntry>>> {
        if (entries.isEmpty()) return emptyList()

        val groups = LinkedHashMap<ChunkKey, MutableList<BlockEntry>>()
        for (e in entries) {
            // rel coords are non-negative within bounds.
            val ck = ChunkKey(e.relX shr 4, e.relY shr 4, e.relZ shr 4)
            groups.computeIfAbsent(ck) { mutableListOf() }.add(e)
        }
        return groups.entries.map { it.key to it.value }
    }

    private fun isBoundary(pos: BlockPos, present: Set<BlockPos>): Boolean {
        // If any neighbor is absent, this cube contributes to the surface.
        for (f in EnumFacing.values()) {
            if (!present.contains(pos.offset(f))) return true
        }
        return false
    }
}
