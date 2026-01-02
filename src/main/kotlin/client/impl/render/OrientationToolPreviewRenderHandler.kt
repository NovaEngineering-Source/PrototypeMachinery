package github.kasuminova.prototypemachinery.client.impl.render

import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.common.item.ControllerOrientationToolItem
import github.kasuminova.prototypemachinery.common.util.TwistMath
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.client.renderer.RenderGlobal
import net.minecraft.client.renderer.RenderHelper
import net.minecraft.client.renderer.Tessellator
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.lwjgl.opengl.GL11
import java.util.Locale

/**
 * World-space hint rendering for [ControllerOrientationToolItem].
 *
 * When the player is holding the orientation tool (enabled) and is looking at a rotatable block
 * (currently: machine controller / [MachineBlockEntity]), render an overlay that explains what
 * will happen on click.
 *
 * Note: intentionally not registered by default (kept as a UX experiment).
 * If you want to enable it, register this handler on the client event bus from ClientProxy.
 */
internal object OrientationToolPreviewRenderHandler {

    private fun String.lc(): String = lowercase(Locale.ROOT)

    @SubscribeEvent
    fun onRenderWorldLast(event: RenderWorldLastEvent) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return
        val world = mc.world ?: return

        val stack = player.heldItemMainhand.takeIf { it.item is ControllerOrientationToolItem }
            ?: player.heldItemOffhand.takeIf { it.item is ControllerOrientationToolItem }
            ?: return

        // Only show hints in enabled mode (matches actual click behavior).
        if (!ControllerOrientationToolItem.isEnabled(stack)) return

        val hit = mc.objectMouseOver ?: return
        if (hit.typeOfHit != net.minecraft.util.math.RayTraceResult.Type.BLOCK) return
        val pos = hit.blockPos ?: return
        val side = hit.sideHit ?: return

        val te = world.getTileEntity(pos) as? MachineBlockEntity ?: return
        val state = world.getBlockState(pos)

        val curFacing = runCatching { state.getValue(MachineBlock.FACING) }.getOrDefault(EnumFacing.NORTH)
        val curTwist = te.twist

        val (nextFacing, nextTwist, actionText) = previewAction(curFacing, curTwist, side, player.isSneaking)

        val partialTicks = event.partialTicks
        val view = mc.renderViewEntity ?: player
        val camX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks
        val camY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks
        val camZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks

        // Outline + direction indicators.
        GlStateManager.pushMatrix()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)
        GlStateManager.disableTexture2D()
        GlStateManager.disableLighting()
        GlStateManager.glLineWidth(2.0f)

        try {
            val bb = safeSelectionBox(world, pos).grow(0.002).offset(-camX, -camY, -camZ)
            RenderGlobal.drawSelectionBoundingBox(bb, 0.2f, 0.9f, 1.0f, 0.55f)

            // Draw current facing arrow (green).
            drawDirectionLine(
                pos = pos,
                dir = curFacing,
                camX = camX,
                camY = camY,
                camZ = camZ,
                r = 0.2f,
                g = 1.0f,
                b = 0.2f,
                a = 0.85f
            )

            // Draw clicked side arrow (yellow).
            drawDirectionLine(
                pos = pos,
                dir = side,
                camX = camX,
                camY = camY,
                camZ = camZ,
                r = 1.0f,
                g = 0.9f,
                b = 0.2f,
                a = 0.85f
            )
        } finally {
            GlStateManager.enableTexture2D()
            GlStateManager.disableBlend()
            GlStateManager.popMatrix()
        }

        // Text overlay (billboard) – render with texture enabled.
        drawFloatingText(
            lines = listOf(
                "[PM] 朝向工具",
                "当前: facing=${curFacing.name.lc()} twist=$curTwist (top=${TwistMath.getTopFromTwist(curFacing, curTwist).name.lc()})",
                "瞄准面: ${side.name.lc()}${if (player.isSneaking) " (潜行)" else ""}",
                "操作: $actionText",
                "结果: facing=${nextFacing.name.lc()} twist=$nextTwist",
            ),
            worldX = pos.x + 0.5,
            worldY = pos.y + 1.35,
            worldZ = pos.z + 0.5,
            camX = camX,
            camY = camY,
            camZ = camZ,
            color = 0xFFFFFF,
        )
    }

    private data class PreviewResult(
        val nextFacing: EnumFacing,
        val nextTwist: Int,
        val actionText: String,
    )

    private fun previewAction(curFacing: EnumFacing, curTwist: Int, clickedSide: EnumFacing, sneaking: Boolean): PreviewResult {
        if (sneaking) {
            // Replicate ControllerOrientationToolItem.nextOrientation() (private there).
            val newTwist = (curTwist + 1) and 3
            if (newTwist != 0) {
                return PreviewResult(curFacing, newTwist, "循环24向: twist +1")
            }
            val facings = EnumFacing.values()
            val idx = facings.indexOf(curFacing)
            val newFacing = facings[(idx + 1) % facings.size]
            return PreviewResult(newFacing, 0, "循环24向: facing -> ${newFacing.name.lc()}, twist=0")
        }

        // Click front face -> rotate twist clockwise
        if (clickedSide == curFacing) {
            val newTwist = (curTwist + 1) and 3
            return PreviewResult(curFacing, newTwist, "点击正面: twist +1")
        }

        // Click other face -> set facing to that direction, reset twist
        return PreviewResult(clickedSide, 0, "点击侧面: facing -> ${clickedSide.name.lc()}, twist=0")
    }

    private fun safeSelectionBox(world: net.minecraft.world.World, pos: BlockPos): AxisAlignedBB {
        val state = world.getBlockState(pos)
        return try {
            state.getSelectedBoundingBox(world, pos)
        } catch (_: Throwable) {
            AxisAlignedBB(pos)
        }
    }

    private fun drawDirectionLine(
        pos: BlockPos,
        dir: EnumFacing,
        camX: Double,
        camY: Double,
        camZ: Double,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ) {
        val startX = pos.x + 0.5 - camX
        val startY = pos.y + 0.5 - camY
        val startZ = pos.z + 0.5 - camZ

        val dx = dir.xOffset.toDouble()
        val dy = dir.yOffset.toDouble()
        val dz = dir.zOffset.toDouble()

        val len = 0.65
        val endX = startX + dx * len
        val endY = startY + dy * len
        val endZ = startZ + dz * len

        val t = Tessellator.getInstance()
        val buf = t.buffer
        buf.begin(GL11.GL_LINES, DefaultVertexFormats.POSITION_COLOR)
        buf.pos(startX, startY, startZ).color(r, g, b, a).endVertex()
        buf.pos(endX, endY, endZ).color(r, g, b, a).endVertex()
        t.draw()
    }

    private fun drawFloatingText(
        lines: List<String>,
        worldX: Double,
        worldY: Double,
        worldZ: Double,
        camX: Double,
        camY: Double,
        camZ: Double,
        color: Int,
    ) {
        val mc = Minecraft.getMinecraft()
        val fr = mc.fontRenderer ?: return
        val rm = mc.renderManager ?: return

        // Distance clamp: avoid rendering far away.
        val dx = (worldX - (camX))
        val dy = (worldY - (camY))
        val dz = (worldZ - (camZ))
        val distSq = dx * dx + dy * dy + dz * dz
        if (distSq > 64.0 * 64.0) return

        GlStateManager.pushMatrix()
        GlStateManager.translate(worldX - camX, worldY - camY, worldZ - camZ)
        GlStateManager.rotate(-rm.playerViewY, 0.0f, 1.0f, 0.0f)
        GlStateManager.rotate(rm.playerViewX, 1.0f, 0.0f, 0.0f)

        // Scale: keep readable.
        val scale = 0.016f
        GlStateManager.scale(-scale, -scale, scale)

        GlStateManager.disableLighting()
        GlStateManager.enableTexture2D()
        GlStateManager.depthMask(false)
        GlStateManager.disableDepth()
        GlStateManager.enableBlend()
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0)

        RenderHelper.disableStandardItemLighting()

        val pad = 2
        val widths = lines.map { fr.getStringWidth(it) }
        val w = (widths.maxOrNull() ?: 0)
        val lineH = fr.FONT_HEIGHT
        val totalH = lines.size * lineH

        // Background quad
        val bgA = 0x66
        val left = -w / 2 - pad
        val right = w / 2 + pad
        val top = -pad
        val bottom = totalH + pad

        val tess = Tessellator.getInstance()
        val bb = tess.buffer
        bb.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR)
        bb.pos(left.toDouble(), bottom.toDouble(), 0.0).color(0, 0, 0, bgA).endVertex()
        bb.pos(right.toDouble(), bottom.toDouble(), 0.0).color(0, 0, 0, bgA).endVertex()
        bb.pos(right.toDouble(), top.toDouble(), 0.0).color(0, 0, 0, bgA).endVertex()
        bb.pos(left.toDouble(), top.toDouble(), 0.0).color(0, 0, 0, bgA).endVertex()
        tess.draw()

        // Text
        var y = 0
        for (line in lines) {
            fr.drawStringWithShadow(line, (-fr.getStringWidth(line) / 2).toFloat(), y.toFloat(), color)
            y += lineH
        }

        GlStateManager.enableDepth()
        GlStateManager.depthMask(true)
        GlStateManager.disableBlend()
        GlStateManager.popMatrix()
    }
}
