package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.common.item.ScannerInstrumentItem
import github.kasuminova.prototypemachinery.common.scanner.ScannerInstrumentNbt
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderGlobal
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Client-side selection rendering for [ScannerInstrumentItem].
 *
 * - Renders an outline at origin/corner block positions (if non-air).
 * - If both are set: renders the selection AABB.
 * - Optionally renders per-block outlines for non-air blocks when the selection is small.
 */
internal object ScannerSelectionRenderHandler {

    /**
     * Render per-block outlines only when region volume is small enough.
     * This does NOT affect export size; it only avoids client-side render stalls.
     */
    private const val MAX_OUTLINE_VOLUME: Long = 16L * 16L * 16L

    /** Hard cap on number of block AABBs drawn per frame (safety net). */
    private const val MAX_OUTLINED_BLOCKS: Int = 16 * 16 * 16

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val world = mc.world ?: return

        val stack = findScannerStack(player.getHeldItemMainhand()) ?: findScannerStack(player.getHeldItemOffhand()) ?: return
        val data = stack.tagCompound?.takeIf { it.hasKey(ScannerInstrumentNbt.TAG_ROOT, 10) }?.getCompoundTag(ScannerInstrumentNbt.TAG_ROOT) ?: return

        val origin = data.getBlockPosOrNull(ScannerInstrumentNbt.TAG_ORIGIN)
        val corner = data.getBlockPosOrNull(ScannerInstrumentNbt.TAG_CORNER)
        if (origin == null && corner == null) return

        val partialTicks = event.partialTicks
        val view = mc.renderViewEntity ?: return
        val camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks
        val camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks
        val camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks

        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.glLineWidth(2.0f)

        // Markers for endpoints (filter air).
        origin?.let { renderBlockOutlineIfNotAir(world, it, camX, camY, camZ, 0.2f, 0.9f, 1.0f, 0.70f) }
        corner?.let { renderBlockOutlineIfNotAir(world, it, camX, camY, camZ, 1.0f, 0.9f, 0.2f, 0.70f) }

        // Region AABB + optional per-block outlines.
        if (origin != null && corner != null) {
            val min = BlockPos(minOf(origin.x, corner.x), minOf(origin.y, corner.y), minOf(origin.z, corner.z))
            val max = BlockPos(maxOf(origin.x, corner.x), maxOf(origin.y, corner.y), maxOf(origin.z, corner.z))
            val sizeX = (max.x - min.x + 1).toLong()
            val sizeY = (max.y - min.y + 1).toLong()
            val sizeZ = (max.z - min.z + 1).toLong()
            val volume = sizeX * sizeY * sizeZ

            // Region bounding box (always render).
            val region = AxisAlignedBB(
                min.x.toDouble(),
                min.y.toDouble(),
                min.z.toDouble(),
                (max.x + 1).toDouble(),
                (max.y + 1).toDouble(),
                (max.z + 1).toDouble(),
            ).offset(-camX, -camY, -camZ)
            RenderGlobal.drawSelectionBoundingBox(region, 0.2f, 1.0f, 0.2f, 0.35f)

            // Per-block outlines for small selections (filter air).
            if (volume in 1..MAX_OUTLINE_VOLUME) {
                var rendered = 0
                renderLoop@ for (x in min.x..max.x) {
                    for (y in min.y..max.y) {
                        for (z in min.z..max.z) {
                            if (rendered >= MAX_OUTLINED_BLOCKS) break@renderLoop
                            val p = BlockPos(x, y, z)
                            val state = world.getBlockState(p)
                            val block = state.block
                            if (block === Blocks.AIR) continue

                            val bb = try {
                                state.getSelectedBoundingBox(world, p)
                            } catch (_: Throwable) {
                                AxisAlignedBB(p).grow(0.002)
                            }

                            RenderGlobal.drawSelectionBoundingBox(bb.grow(0.002).offset(-camX, -camY, -camZ), 0.35f, 0.9f, 0.35f, 0.20f)
                            rendered++
                        }
                    }
                }
            }
        }

        GlStateManager.enableTexture2D()
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }

    private fun renderBlockOutlineIfNotAir(
        world: net.minecraft.world.World,
        pos: BlockPos,
        camX: Double,
        camY: Double,
        camZ: Double,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ) {
        val state = world.getBlockState(pos)
        if (state.block === Blocks.AIR) return

        val bb = try {
            state.getSelectedBoundingBox(world, pos)
        } catch (_: Throwable) {
            AxisAlignedBB(pos)
        }

        RenderGlobal.drawSelectionBoundingBox(bb.grow(0.002).offset(-camX, -camY, -camZ), r, g, b, a)
    }

    private fun findScannerStack(stack: ItemStack): ItemStack? {
        if (stack.isEmpty) return null
        return if (stack.item is ScannerInstrumentItem) stack else null
    }

    private fun NBTTagCompound.getBlockPosOrNull(key: String): BlockPos? {
        if (!hasKey(key, 10)) return null
        val tag = getCompoundTag(key)
        return BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"))
    }
}
