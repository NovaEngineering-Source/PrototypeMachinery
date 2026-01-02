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
import net.minecraft.util.text.TextComponentString
import net.minecraft.world.World

/**
 * Range structure selector.
 *
 * 用于整合包作者/开发者从世界中“框选并导出”结构 JSON。
 */
internal class ScannerInstrumentItem : Item(), IGuiHolder<PlayerInventoryGuiData> {

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

        // Sneak + both points set => export.
        if (isSneaking && origin != null && corner != null) {
            val sel = computeSelection(origin, corner)
            if (sel.volume > WARN_EXPORT_VOLUME) {
                player.sendMessage(TextComponentString("[PM] 注意：选区较大（blocks=${sel.volume}），导出可能耗时/卡服。"))
            }

            val suggested = stack.displayName
            val rawId = data.getString(ScannerInstrumentNbt.TAG_STRUCTURE_ID).takeIf { it.isNotBlank() }
                ?: suggested
                ?: "scan_${player.name}_${worldIn.totalWorldTime}"
            val structureId = StructureExportUtil.sanitizeId(rawId)

            val displayName = data.getString(ScannerInstrumentNbt.TAG_LANG_NAME).takeIf { it.isNotBlank() }
            val includeTileNbtConstraints = data.getBoolean(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT)

            val structure = StructureExportUtil.exportWorldSelectionAsTemplate(
                world = worldIn,
                origin = origin,
                corner = corner,
                structureId = structureId,
                displayName = displayName,
                includeAir = false,
                includeTileNbtConstraints = includeTileNbtConstraints,
            )

            val file = StructureExportUtil.writeStructureJson(
                data = structure,
                subDir = "scanned",
                preferredFileName = structureId,
            )

            ScannerInstrumentNbt.clearSelection(data)
            stack.tagCompound = stack.tagCompound // keep

            player.sendMessage(TextComponentString("[PM] 结构已导出: id=$structureId"))
            player.sendMessage(TextComponentString("[PM] 选区 blocks=${sel.volume} NBT=${if (includeTileNbtConstraints) "ON" else "OFF"}"))
            player.sendMessage(TextComponentString("[PM] 文件: ${file.absolutePath}"))
            player.sendMessage(TextComponentString("[PM] 提示: 结构 JSON 需要重启/重载后才会被加载（当前暂无在线重载）。"))
            return EnumActionResult.SUCCESS
        }

        // Sneak + only origin set => clear selection.
        if (isSneaking && origin != null && corner == null) {
            ScannerInstrumentNbt.clearSelection(data)
            player.sendMessage(TextComponentString("[PM] 已清空选择（origin/corner）。"))
            return EnumActionResult.SUCCESS
        }

        // Non-export interactions: set origin/corner.
        if (origin == null) {
            ScannerInstrumentNbt.writeOrigin(data, pos)
            data.removeTag(ScannerInstrumentNbt.TAG_CORNER)
            // Cache a suggested id based on current name (anvil rename) for future export.
            data.setString(ScannerInstrumentNbt.TAG_STRUCTURE_ID, StructureExportUtil.sanitizeId(stack.displayName))

            player.sendMessage(TextComponentString("[PM] 已设置 origin: ${pos.x},${pos.y},${pos.z}"))
            player.sendMessage(TextComponentString("[PM] 再右键一个角点设置范围；潜行右键导出。"))
            return EnumActionResult.SUCCESS
        }

        if (corner == null) {
            ScannerInstrumentNbt.writeCorner(data, pos)
            val sel = computeSelection(origin, pos)
            player.sendMessage(TextComponentString("[PM] 已设置 corner: ${pos.x},${pos.y},${pos.z}"))
            player.sendMessage(
                TextComponentString(
                    "[PM] 选区: min=${sel.min.x},${sel.min.y},${sel.min.z} max=${sel.max.x},${sel.max.y},${sel.max.z} size=${sel.size.x}x${sel.size.y}x${sel.size.z} blocks=${sel.volume}"
                )
            )
            if (sel.volume > WARN_EXPORT_VOLUME) {
                player.sendMessage(TextComponentString("[PM] 警告：选区较大（blocks=${sel.volume}），导出可能耗时/卡服。"))
            }
            player.sendMessage(TextComponentString("[PM] 潜行右键任意方块导出结构 JSON。"))
            return EnumActionResult.SUCCESS
        }

        // Both points already set and not sneaking: update corner for convenience.
        ScannerInstrumentNbt.writeCorner(data, pos)
        player.sendMessage(TextComponentString("[PM] 已更新 corner: ${pos.x},${pos.y},${pos.z}（潜行右键导出）"))
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
