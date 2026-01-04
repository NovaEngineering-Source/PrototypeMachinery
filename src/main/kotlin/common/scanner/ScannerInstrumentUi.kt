package github.kasuminova.prototypemachinery.common.scanner

import com.cleanroommc.modularui.api.ISyncedAction
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.DoubleSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.value.sync.StringSyncValue
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.common.block.MachineBlock
import github.kasuminova.prototypemachinery.common.block.entity.MachineBlockEntity
import github.kasuminova.prototypemachinery.common.structure.tools.StructureExportUtil
import net.minecraft.network.PacketBuffer
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentString
import kotlin.math.roundToInt

/**
 * Scanner Instrument UI (synced PlayerInventory GUI).
 *
 * 说明：该面板会在服务端也被构建（用于收集 sync values），因此这里必须避免任何 net.minecraft.client.* 引用。
 */
internal object ScannerInstrumentUi {

    private const val PANEL_W = 384
    private const val PANEL_H = 228

    private const val ACTION_KEY = "pm:scanner_instrument_action"

    /** Safety cap to avoid stalling server tick when users select giant areas. */
    private const val AUTO_SCAN_MAX_VOLUME: Long = 64L * 64L * 64L

    private val BG: UITexture = UITexture.builder()
        .imageSize(384, 256)
        .subAreaXYWH(0, 0, PANEL_W, PANEL_H)
        .location(ResourceLocation(PrototypeMachinery.MOD_ID, "gui/gui_scanner_instrument/base"))
        .build()

    enum class Action(val id: Int) {
        EXPORT(0),
        CLEAR(1),
        RESET(2),
        SET_ORIGIN_FROM_EDIT(3),
        SET_ORIGIN_TO(4),
        RESET_PREVIEW_ORIENTATION(5),
        SET_PREVIEW_FACING(6),
        SET_PREVIEW_ROT(7)
    }

    fun build(data: PlayerInventoryGuiData, syncManager: PanelSyncManager, settings: UISettings): ModularPanel {
        // One-time server-side auto init for export origin + preview orientation.
        if (!data.player.world.isRemote) {
            val stack = data.player.inventory.getStackInSlot(data.slotIndex)
            if (!stack.isEmpty) {
                val tag = ScannerInstrumentNbt.getOrCreateData(stack)
                ScannerInstrumentNbt.ensureDefaults(stack, tag)
                tryAutoInitFromController(data.player.world, tag)
                stack.tagCompound = stack.tagCompound
            }
        }

        // --- Actions (client -> server) ---
        syncManager.registerSyncedAction(
            ACTION_KEY,
            /* executeClient = */ false,
            /* executeServer = */ true,
            ISyncedAction { buf ->
                val actionId = buf.readVarInt()
                val player = data.player
                val stack = player.inventory.getStackInSlot(data.slotIndex)
                if (stack.isEmpty) return@ISyncedAction

                val tag = ScannerInstrumentNbt.getOrCreateData(stack)
                ScannerInstrumentNbt.ensureDefaults(stack, tag)

                when (actionId) {
                    Action.EXPORT.id -> {
                        val selA = ScannerInstrumentNbt.readOrigin(tag)
                        val selB = ScannerInstrumentNbt.readCorner(tag)
                        if (selA == null || selB == null) {
                               player.sendMessage(TextComponentString("[PM] 请先用物品在世界中设置 origin/corner（右键方块两次）"))
                            player.sendMessage(TextComponentString("[PM] 提示：潜行右键方块可重新选择 origin（会清空 corner）。"))
                            return@ISyncedAction
                        }

                        // Auto init right before export as well (covers cases where player selected after opening UI).
                        tryAutoInitFromController(player.world, tag)

                        val selectionMin = BlockPos(
                            minOf(selA.x, selB.x),
                            minOf(selA.y, selB.y),
                            minOf(selA.z, selB.z)
                        )
                        val selectionMax = BlockPos(
                            maxOf(selA.x, selB.x),
                            maxOf(selA.y, selB.y),
                            maxOf(selA.z, selB.z)
                        )

                        val exportOrigin = ScannerInstrumentNbt.readEffectiveExportOrigin(tag)
                            ?: selA

                        val suggested = stack.displayName
                        val rawId = tag.getString(ScannerInstrumentNbt.TAG_STRUCTURE_ID).takeIf { it.isNotBlank() }
                            ?: suggested
                            ?: "scan_${player.name}_${player.world.totalWorldTime}"
                        val structureId = StructureExportUtil.sanitizeId(rawId)

                        val langName = tag.getString(ScannerInstrumentNbt.TAG_LANG_NAME).takeIf { it.isNotBlank() }
                        val includeTileNbtConstraints = tag.getBoolean(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT)

                        val rotation = ScannerInstrumentNbt.worldToTemplateRotationFromPreview(tag)

                        val structure = StructureExportUtil.exportWorldBoxAsTemplate(
                            world = player.world,
                            selectionMin = selectionMin,
                            selectionMax = selectionMax,
                            exportOrigin = exportOrigin,
                            structureId = structureId,
                            displayName = langName,
                            includeAir = false,
                            includeTileNbtConstraints = includeTileNbtConstraints,
                            rotationWorldToTemplate = rotation,
                        )

                        val file = StructureExportUtil.writeStructureJson(
                            data = structure,
                            subDir = "scanned",
                            preferredFileName = structureId,
                        )

                        // Clear selection after export.
                        ScannerInstrumentNbt.clearSelection(tag)

                        player.sendMessage(TextComponentString("[PM] 结构已导出: id=$structureId"))
                        player.sendMessage(TextComponentString("[PM] NBT=${if (includeTileNbtConstraints) "ON" else "OFF"}"))
                        player.sendMessage(TextComponentString("[PM] 导出原点: ${exportOrigin.x},${exportOrigin.y},${exportOrigin.z}"))
                        player.sendMessage(TextComponentString("[PM] 文件: ${file.absolutePath}"))
                        player.sendMessage(TextComponentString("[PM] 提示: 结构 JSON 需要重启/重载后才会被加载（当前暂无在线重载）。"))
                    }

                    Action.CLEAR.id -> {
                        ScannerInstrumentNbt.clearSelection(tag)
                    }

                    Action.RESET.id -> {
                        // Reset UI-facing fields to defaults.
                        ScannerInstrumentNbt.clearSelection(tag)
                        tag.setString(ScannerInstrumentNbt.TAG_LANG_NAME, "")
                        tag.setString(ScannerInstrumentNbt.TAG_STRUCTURE_ID, StructureExportUtil.sanitizeId(stack.displayName))
                        tag.setBoolean(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT, false)

                        tag.setBoolean(ScannerInstrumentNbt.TAG_EXPANDED, false)
                        tag.setInteger(ScannerInstrumentNbt.TAG_EXPANDED_QUANTITY, 1)
                        tag.setInteger(ScannerInstrumentNbt.TAG_EXPANDED_SPACING, 0)

                        tag.setBoolean(ScannerInstrumentNbt.TAG_SUBSTRUCTURE, false)
                        tag.setBoolean(ScannerInstrumentNbt.TAG_ALLOW_MIRROR, false)

                        tag.setInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_X, 0)
                        tag.setInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Y, 0)
                        tag.setInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Z, 0)

                        tag.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
                        tag.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT, 0)
                        tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)

                        tag.setBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO, true)
                        tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, true)
                    }

                    Action.SET_ORIGIN_FROM_EDIT.id -> {
                        val x = tag.getInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_X)
                        val y = tag.getInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Y)
                        val z = tag.getInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Z)
                        ScannerInstrumentNbt.writeExportOrigin(tag, BlockPos(x, y, z))
                        tag.setBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO, false)
                    }

                    Action.SET_ORIGIN_TO.id -> {
                        val x = buf.readInt()
                        val y = buf.readInt()
                        val z = buf.readInt()
                        ScannerInstrumentNbt.writeExportOrigin(tag, BlockPos(x, y, z))
                        tag.setBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO, false)
                    }

                    Action.RESET_PREVIEW_ORIENTATION.id -> {
                        tag.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
                        tag.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT, 0)
                        tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)

                        // Allow controller auto init to re-apply on next open/export.
                        tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, true)
                    }

                    Action.SET_PREVIEW_FACING.id -> {
                        val faceIdx = buf.readVarInt()
                        val facing = EnumFacing.values().getOrNull(faceIdx) ?: EnumFacing.NORTH
                        ScannerInstrumentNbt.writePreviewFacing(tag, facing)

                        // User override.
                        tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, false)
                    }

                    Action.SET_PREVIEW_ROT.id -> {
                        val rot = buf.readVarInt().coerceIn(0, 3)
                        ScannerInstrumentNbt.writePreviewRot(tag, rot)

                        // User override.
                        tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO, false)
                    }
                }

                // keep (force dirty)
                stack.tagCompound = stack.tagCompound
            }
        )

        // --- Sync values (server <-> client) ---
        fun withData(block: (tag: net.minecraft.nbt.NBTTagCompound) -> Unit) {
            val stack = data.player.inventory.getStackInSlot(data.slotIndex)
            if (stack.isEmpty) return
            val tag = ScannerInstrumentNbt.getOrCreateData(stack)
            ScannerInstrumentNbt.ensureDefaults(stack, tag)
            block(tag)
            stack.tagCompound = stack.tagCompound
        }

        fun readString(key: String, fallback: String = ""): String {
            val stack = data.player.inventory.getStackInSlot(data.slotIndex)
            val tag = ScannerInstrumentNbt.getDataOrNull(stack) ?: return fallback
            return tag.getString(key)
        }

        fun readBool(key: String, fallback: Boolean = false): Boolean {
            val stack = data.player.inventory.getStackInSlot(data.slotIndex)
            val tag = ScannerInstrumentNbt.getDataOrNull(stack) ?: return fallback
            return tag.getBoolean(key)
        }

        fun readInt(key: String, fallback: Int = 0): Int {
            val stack = data.player.inventory.getStackInSlot(data.slotIndex)
            val tag = ScannerInstrumentNbt.getDataOrNull(stack) ?: return fallback
            return tag.getInteger(key)
        }

        // langName
        syncManager.syncValue(
            "langName",
            StringSyncValue(
                { readString(ScannerInstrumentNbt.TAG_LANG_NAME) },
                { s -> withData { it.setString(ScannerInstrumentNbt.TAG_LANG_NAME, s) } }
            )
        )

        // structureId (sanitize on server)
        syncManager.syncValue(
            "structureId",
            StringSyncValue(
                { readString(ScannerInstrumentNbt.TAG_STRUCTURE_ID) },
                { s ->
                    withData { it.setString(ScannerInstrumentNbt.TAG_STRUCTURE_ID, StructureExportUtil.sanitizeId(s)) }
                }
            )
        )

        // expanded enable
        syncManager.syncValue(
            "expandedEnabled",
            BooleanSyncValue(
                { readBool(ScannerInstrumentNbt.TAG_EXPANDED) },
                { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_EXPANDED, v) } }
            )
        )

        // expandedQuantity (int) - used by both TextField + Slider
        syncManager.syncValue(
            "expandedQuantityStr",
            StringSyncValue(
                { readInt(ScannerInstrumentNbt.TAG_EXPANDED_QUANTITY, 1).toString() },
                { s ->
                    val v = s.toIntOrNull() ?: 1
                    withData { it.setInteger(ScannerInstrumentNbt.TAG_EXPANDED_QUANTITY, v.coerceAtLeast(1)) }
                }
            )
        )
        syncManager.syncValue(
            "expandedQuantity",
            DoubleSyncValue(
                { readInt(ScannerInstrumentNbt.TAG_EXPANDED_QUANTITY, 1).toDouble() },
                { d ->
                    val v = d.roundToInt().coerceAtLeast(1)
                    withData { it.setInteger(ScannerInstrumentNbt.TAG_EXPANDED_QUANTITY, v) }
                }
            )
        )

        // expandedSpacing (int)
        syncManager.syncValue(
            "expandedSpacingStr",
            StringSyncValue(
                { readInt(ScannerInstrumentNbt.TAG_EXPANDED_SPACING, 0).toString() },
                { s ->
                    val v = s.toIntOrNull() ?: 0
                    withData { it.setInteger(ScannerInstrumentNbt.TAG_EXPANDED_SPACING, v.coerceAtLeast(0)) }
                }
            )
        )
        syncManager.syncValue(
            "expandedSpacing",
            DoubleSyncValue(
                { readInt(ScannerInstrumentNbt.TAG_EXPANDED_SPACING, 0).toDouble() },
                { d ->
                    val v = d.roundToInt().coerceAtLeast(0)
                    withData { it.setInteger(ScannerInstrumentNbt.TAG_EXPANDED_SPACING, v) }
                }
            )
        )

        // structure switches
        syncManager.syncValue(
            "substructure",
            BooleanSyncValue(
                { readBool(ScannerInstrumentNbt.TAG_SUBSTRUCTURE) },
                { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_SUBSTRUCTURE, v) } }
            )
        )
        syncManager.syncValue(
            "matchNbt",
            BooleanSyncValue(
                { readBool(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT) },
                { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT, v) } }
            )
        )
        syncManager.syncValue(
            "allowMirror",
            BooleanSyncValue(
                { readBool(ScannerInstrumentNbt.TAG_ALLOW_MIRROR) },
                { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_ALLOW_MIRROR, v) } }
            )
        )

        // origin edit buffer
        syncManager.syncValue(
            "originEditX",
            StringSyncValue(
                { readInt(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_X, 0).toString() },
                { s -> withData { it.setInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_X, s.toIntOrNull() ?: 0) } }
            )
        )
        syncManager.syncValue(
            "originEditY",
            StringSyncValue(
                { readInt(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Y, 0).toString() },
                { s -> withData { it.setInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Y, s.toIntOrNull() ?: 0) } }
            )
        )
        syncManager.syncValue(
            "originEditZ",
            StringSyncValue(
                { readInt(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Z, 0).toString() },
                { s -> withData { it.setInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Z, s.toIntOrNull() ?: 0) } }
            )
        )

        // mirror preview (choose switch)
        syncManager.syncValue(
            "previewMirror",
            BooleanSyncValue(
                { readBool(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR) },
                { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, v) } }
            )
        )

        val panel = ModularPanel.defaultPanel("scanner_instrument")
            .size(PANEL_W, PANEL_H)
            .background(BG)

        val root = Column().pos(0, 0).size(PANEL_W, PANEL_H)
        panel.child(root)

        val tagProvider: () -> net.minecraft.nbt.NBTTagCompound? = {
            val stack = data.player.inventory.getStackInSlot(data.slotIndex)
            ScannerInstrumentNbt.getDataOrNull(stack)
        }

        // Client-only widgets (layout + textures + click handling)
        if (data.player.world.isRemote) {
            PrototypeMachinery.proxy.addScannerInstrumentClientWidgets(root, tagProvider, syncManager)
        }

        return panel
    }

    private data class ControllerHit(
        val pos: BlockPos,
        val facing: EnumFacing,
        val twist: Int,
    )

    /**
        * If selection contains exactly one controller, auto-fill export origin + preview orientation.
        *
        * Rules:
        * - Only runs when selection endpoints are present.
        * - Only applies exportOrigin when TAG_EXPORT_ORIGIN_AUTO is true.
        * - Only applies previewFacing/rot when TAG_PREVIEW_AUTO is true.
        */
    private fun tryAutoInitFromController(world: net.minecraft.world.World, tag: net.minecraft.nbt.NBTTagCompound) {
        val a = ScannerInstrumentNbt.readOrigin(tag) ?: return
        val b = ScannerInstrumentNbt.readCorner(tag) ?: return

        val min = BlockPos(minOf(a.x, b.x), minOf(a.y, b.y), minOf(a.z, b.z))
        val max = BlockPos(maxOf(a.x, b.x), maxOf(a.y, b.y), maxOf(a.z, b.z))
        val vol = (max.x - min.x + 1).toLong() * (max.y - min.y + 1).toLong() * (max.z - min.z + 1).toLong()
        if (vol <= 0L || vol > AUTO_SCAN_MAX_VOLUME) return

        val hit = findSingleController(world, min, max) ?: return

        if (tag.getBoolean(ScannerInstrumentNbt.TAG_EXPORT_ORIGIN_AUTO)) {
            ScannerInstrumentNbt.writeExportOrigin(tag, hit.pos)
        }
        if (tag.getBoolean(ScannerInstrumentNbt.TAG_PREVIEW_AUTO)) {
            ScannerInstrumentNbt.writePreviewFacing(tag, hit.facing)
            ScannerInstrumentNbt.writePreviewRot(tag, hit.twist)
            tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)
        }
    }

    private fun findSingleController(
        world: net.minecraft.world.World,
        min: BlockPos,
        max: BlockPos,
    ): ControllerHit? {
        var found: ControllerHit? = null
        for (y in min.y..max.y) {
            for (z in min.z..max.z) {
                for (x in min.x..max.x) {
                    val p = BlockPos(x, y, z)
                    val st = world.getBlockState(p)
                    val blk = st.block
                    if (blk !is MachineBlock) continue

                    val facing = try {
                        st.getValue(MachineBlock.FACING)
                    } catch (_: Throwable) {
                        EnumFacing.NORTH
                    }
                    val twist = (world.getTileEntity(p) as? MachineBlockEntity)?.twist ?: 0

                    val hit = ControllerHit(p, facing, twist)
                    if (found != null) {
                        // Multiple controllers: ambiguous, skip auto.
                        return null
                    }
                    found = hit
                }
            }
        }
        return found
    }

    /** Helper for client code to send action packets to server. */
    fun callAction(syncManager: PanelSyncManager, action: Action, write: (PacketBuffer) -> Unit = {}) {
        syncManager.callSyncedAction(ACTION_KEY) { buf ->
            buf.writeVarInt(action.id)
            write(buf)
        }
    }
}
