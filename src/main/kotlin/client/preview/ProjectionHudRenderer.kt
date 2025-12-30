package github.kasuminova.prototypemachinery.client.preview

import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.client.util.ItemStackDisplayUtil
import net.minecraft.block.Block
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.client.resources.I18n
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation

/**
 * HUD overlay rendering for structure projection preview information.
 *
 * 结构投影预览的 HUD 信息展示。
 */
internal object ProjectionHudRenderer {

    private const val KEY_MODE_FOLLOWING: String = "pm.projection.mode.following"
    private const val KEY_MODE_LOCKED: String = "pm.projection.mode.locked"

    private const val KEY_HUD_ORIENTATION_STATUS: String = "pm.projection.hud.orientation_status"

    private const val KEY_CHAT_ORIENTATION_LOCKED: String = "pm.projection.chat.locked"
    private const val KEY_CHAT_ORIENTATION_FOLLOWING: String = "pm.projection.chat.following"
    private const val KEY_CHAT_ORIENTATION_ROTATED: String = "pm.projection.chat.rotated"

    private const val KEY_HUD_REQUIREMENT_EXACT: String = "pm.projection.hud.requirement.exact"
    private const val KEY_HUD_REQUIREMENT_EXACT_FALLBACK: String = "pm.projection.hud.requirement.exact_fallback"
    private const val KEY_HUD_REQUIREMENT_GENERIC: String = "pm.projection.hud.requirement.generic"

    /**
     * Render requirement block information under cursor (raycast).
     * 渲染光标下的需求方块信息。
     */
    fun renderBlockInfoOverlay(
        event: net.minecraftforge.client.event.RenderGameOverlayEvent.Text,
        hitPos: BlockPos,
        req: github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement,
        anchor: BlockPos
    ) {
        val text = formatRequirementText(req)
        event.left.add(text)
    }

    /**
     * Render orientation status (locked/following) to HUD.
     * 渲染朝向状态（锁定/跟随）到 HUD。
     */
    fun renderOrientationStatus(
        event: net.minecraftforge.client.event.RenderGameOverlayEvent.Text,
        session: StructureProjectionSession
    ) {
        val o = session.currentOrientation ?: session.lockedOrientation ?: return
        val mode = I18n.format(if (session.followPlayerFacing) KEY_MODE_FOLLOWING else KEY_MODE_LOCKED)
        event.left.add(I18n.format(KEY_HUD_ORIENTATION_STATUS, mode, o.front, o.top))
    }

    /**
     * Send player message about orientation change.
     * 向玩家发送朝向变更信息。
     */
    fun sendOrientationLockedMessage(player: EntityPlayerSP, front: net.minecraft.util.EnumFacing, top: net.minecraft.util.EnumFacing) {
        player.sendMessage(TextComponentTranslation(KEY_CHAT_ORIENTATION_LOCKED, front, top))
    }

    /**
     * Send player message about following player facing.
     * 向玩家发送跟随玩家信息。
     */
    fun sendFollowingPlayerMessage(player: EntityPlayerSP) {
        player.sendMessage(TextComponentTranslation(KEY_CHAT_ORIENTATION_FOLLOWING))
    }

    /**
     * Send player message about orientation rotation.
     * 向玩家发送朝向旋转信息。
     */
    fun sendOrientationRotatedMessage(player: EntityPlayerSP, front: net.minecraft.util.EnumFacing, top: net.minecraft.util.EnumFacing, axis: net.minecraft.util.EnumFacing) {
        player.sendMessage(TextComponentTranslation(KEY_CHAT_ORIENTATION_ROTATED, front, top, axis))
    }

    // ====== Helpers ======

    private fun formatRequirementText(req: github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement): String {
        return when (req) {
            is ExactBlockStateRequirement -> {
                val block = Block.REGISTRY.getObject(req.blockId)
                if (block != null && block != Blocks.AIR) {
                    val stack = ItemStackDisplayUtil.stackForBlock(block, req.meta, 1)
                    val name = try {
                        stack?.displayName ?: req.blockId.toString()
                    } catch (_: Throwable) {
                        req.blockId.toString()
                    }
                    I18n.format(KEY_HUD_REQUIREMENT_EXACT, name, req.blockId.toString(), req.meta.toString())
                } else {
                    I18n.format(KEY_HUD_REQUIREMENT_EXACT_FALLBACK, req.blockId.toString(), req.meta.toString())
                }
            }

            else -> I18n.format(KEY_HUD_REQUIREMENT_GENERIC, req.stableKey())
        }
    }
}
