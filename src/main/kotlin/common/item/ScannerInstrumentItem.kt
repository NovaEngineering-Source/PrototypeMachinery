package github.kasuminova.prototypemachinery.common.item

import com.cleanroommc.modularui.api.IGuiHolder
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData
import com.cleanroommc.modularui.factory.PlayerInventoryGuiFactory
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.scanner.ScannerInstrumentNbt
import github.kasuminova.prototypemachinery.common.scanner.ScannerInstrumentUi
import github.kasuminova.prototypemachinery.common.structure.tools.StructureExportUtil
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.ActionResult
import net.minecraft.util.EnumActionResult
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation
import net.minecraft.world.World

/**
 * Range structure selector.
 *
 * 用于整合包作者/开发者从世界中“框选并导出”结构 JSON。
 */
internal class ScannerInstrumentItem : Item(), IGuiHolder<PlayerInventoryGuiData> {

    private fun msg(player: EntityPlayer, key: String, vararg args: Any) {
        player.sendMessage(TextComponentTranslation(key, *args))
    }

    init {
        registryName = ResourceLocation(PrototypeMachinery.MOD_ID, "scanner_instrument")
        translationKey = "${PrototypeMachinery.MOD_ID}.scanner_instrument"
        maxStackSize = 1

        setCreativeTab(github.kasuminova.prototypemachinery.common.registry.PMCreativeTabs.MAIN)
    }

    override fun buildUI(data: PlayerInventoryGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        // Keep this server-safe: Panel is constructed on BOTH sides (server collects sync values).
        return ScannerInstrumentUi.build(data, syncManager, settings)
    }

    override fun onItemRightClick(worldIn: World, playerIn: EntityPlayer, handIn: EnumHand): ActionResult<ItemStack> {
        val stack = playerIn.getHeldItem(handIn)
        if (stack.isEmpty) return ActionResult(EnumActionResult.PASS, stack)

        if (!worldIn.isRemote) {
            // Ensure defaults for better UX (structureId, preview defaults, etc.)
            val data = ScannerInstrumentNbt.getOrCreateData(stack)
            ScannerInstrumentNbt.ensureDefaults(stack, data)

            // Item responsibility: selecting blocks + opening UI.
            // Export is UI-only.
            PlayerInventoryGuiFactory.INSTANCE.openFromHand(playerIn, handIn)
        }

        return ActionResult(EnumActionResult.SUCCESS, stack)
    }

    override fun onItemUse(
        player: EntityPlayer,
        worldIn: World,
        pos: BlockPos,
        hand: EnumHand,
        facing: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float
    ): EnumActionResult {
        val stack = player.getHeldItem(hand)
        if (stack.isEmpty) return EnumActionResult.PASS

        if (worldIn.isRemote) {
            // Let the server do actual export/nbt write.
            return EnumActionResult.SUCCESS
        }

        val data = ScannerInstrumentNbt.getOrCreateData(stack)
        ScannerInstrumentNbt.ensureDefaults(stack, data)

        val origin = ScannerInstrumentNbt.readOrigin(data)
        val corner = ScannerInstrumentNbt.readCorner(data)

        val isSneaking = player.isSneaking

        // New UX: Sneak + right click BLOCK => (re)select origin and clear corner.
        // Export is UI-only.
        if (isSneaking) {
            ScannerInstrumentNbt.writeOrigin(data, pos)
            data.removeTag(ScannerInstrumentNbt.TAG_CORNER)

            // New selection: reset export/preview overrides to AUTO.
            data.removeTag(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN)
            data.setBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO, true)
            data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, true)
            data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
            data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT, 0)
            data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)

            // Cache a suggested id based on current name (anvil rename) for future export.
            data.setString(ScannerInstrumentNbt.TAG_STRUCTURE_ID, StructureExportUtil.sanitizeId(stack.displayName))

            msg(player, "pm.scanner.chat.origin_set_and_corner_cleared", pos.x, pos.y, pos.z)
            msg(player, "pm.scanner.chat.hint_set_corner_then_export")
            return EnumActionResult.SUCCESS
        }

        // Non-export interactions: set origin/corner.
        if (origin == null) {
            ScannerInstrumentNbt.writeOrigin(data, pos)
            data.removeTag(ScannerInstrumentNbt.TAG_CORNER)

            // New selection: reset export/preview overrides to AUTO.
            data.removeTag(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN)
            data.setBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO, true)
            data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, true)
            data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
            data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT, 0)
            data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)

            // Cache a suggested id based on current name (anvil rename) for future export.
            data.setString(ScannerInstrumentNbt.TAG_STRUCTURE_ID, StructureExportUtil.sanitizeId(stack.displayName))

            msg(player, "pm.scanner.chat.origin_set", pos.x, pos.y, pos.z)
            msg(player, "pm.scanner.chat.hint_set_corner_then_export")
            return EnumActionResult.SUCCESS
        }

        if (corner == null) {
            ScannerInstrumentNbt.writeCorner(data, pos)

            // Selection completed/changed: allow auto re-detect.
            data.removeTag(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN)
            data.setBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO, true)
            data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, true)
            data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
            data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT, 0)
            data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)

            val sel = computeSelection(origin, pos)

            msg(player, "pm.scanner.chat.corner_set", pos.x, pos.y, pos.z)
            msg(
                player,
                "pm.scanner.chat.selection",
                sel.min.x, sel.min.y, sel.min.z,
                sel.max.x, sel.max.y, sel.max.z,
                sel.size.x, sel.size.y, sel.size.z,
                sel.volume
            )
            if (sel.volume > WARN_EXPORT_VOLUME) {
                msg(player, "pm.scanner.chat.warn_large_selection", sel.volume)
            }

            msg(player, "pm.scanner.chat.hint_export")
            return EnumActionResult.SUCCESS
        }

        // Both points already set and not sneaking: update corner for convenience.
        ScannerInstrumentNbt.writeCorner(data, pos)

        // Selection changed: allow auto re-detect.
        data.removeTag(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN)
        data.setBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO, true)
        data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, true)
        data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
        data.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT, 0)
        data.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)

        msg(player, "pm.scanner.chat.corner_updated", pos.x, pos.y, pos.z)
        return EnumActionResult.SUCCESS
    }

    /**
     * Called before Block#onBlockActivated.
     *
     * This ensures the scanner can select blocks that have right-click interactions (GUI, etc.)
     * without the block consuming the click first.
     */
    override fun onItemUseFirst(
        player: EntityPlayer,
        worldIn: World,
        pos: BlockPos,
        side: EnumFacing,
        hitX: Float,
        hitY: Float,
        hitZ: Float,
        hand: EnumHand
    ): EnumActionResult {
        return onItemUse(player, worldIn, pos, hand, side, hitX, hitY, hitZ)
    }

    private data class Selection(
        val min: BlockPos,
        val max: BlockPos,
        val size: BlockPos,
        val volume: Long,
    )

    private fun computeSelection(a: BlockPos, b: BlockPos): Selection {
        val min = BlockPos(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
        val max = BlockPos(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
        val size = BlockPos(max.x - min.x + 1, max.y - min.y + 1, max.z - min.z + 1)
        val volume = size.x.toLong() * size.y.toLong() * size.z.toLong()
        return Selection(min = min, max = max, size = size, volume = volume)
    }

    private companion object {
        /** Warning threshold (does not block export). */
        private const val WARN_EXPORT_VOLUME: Long = 64L * 64L * 64L
    }
}
