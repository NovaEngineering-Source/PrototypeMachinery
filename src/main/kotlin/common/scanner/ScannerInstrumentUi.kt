package github.kasuminova.prototypemachinery.common.scanner

import com.cleanroommc.modularui.api.ISyncedAction
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.factory.PlayerInventoryGuiData
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.utils.BooleanConsumer
import com.cleanroommc.modularui.value.sync.BooleanSyncValue
import com.cleanroommc.modularui.value.sync.DoubleSyncValue
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.value.sync.StringSyncValue
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.PrototypeMachinery
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
                        val origin = ScannerInstrumentNbt.readOrigin(tag)
                        val corner = ScannerInstrumentNbt.readCorner(tag)
                        if (origin == null || corner == null) {
                            player.sendMessage(TextComponentString("[PM] 请先用物品在世界中设置 origin/corner（右键方块两次）"))
                            return@ISyncedAction
                        }

                        val suggested = stack.displayName
                        val rawId = tag.getString(ScannerInstrumentNbt.TAG_STRUCTURE_ID).takeIf { it.isNotBlank() }
                            ?: suggested
                            ?: "scan_${player.name}_${player.world.totalWorldTime}"
                        val structureId = StructureExportUtil.sanitizeId(rawId)

                        val langName = tag.getString(ScannerInstrumentNbt.TAG_LANG_NAME).takeIf { it.isNotBlank() }
                        val includeTileNbtConstraints = tag.getBoolean(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT)

                        val structure = StructureExportUtil.exportWorldSelectionAsTemplate(
                            world = player.world,
                            origin = origin,
                            corner = corner,
                            structureId = structureId,
                            displayName = langName,
                            includeAir = false,
                            includeTileNbtConstraints = includeTileNbtConstraints,
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
                    }

                    Action.SET_ORIGIN_FROM_EDIT.id -> {
                        val x = tag.getInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_X)
                        val y = tag.getInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Y)
                        val z = tag.getInteger(ScannerInstrumentNbt.TAG_ORIGIN_EDIT_Z)
                        ScannerInstrumentNbt.writeOrigin(tag, BlockPos(x, y, z))
                    }

                    Action.SET_ORIGIN_TO.id -> {
                        val x = buf.readInt()
                        val y = buf.readInt()
                        val z = buf.readInt()
                        ScannerInstrumentNbt.writeOrigin(tag, BlockPos(x, y, z))
                    }

                    Action.RESET_PREVIEW_ORIENTATION.id -> {
                        tag.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
                        tag.setInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT, 0)
                        tag.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, false)
                    }

                    Action.SET_PREVIEW_FACING.id -> {
                        val faceIdx = buf.readVarInt()
                        val facing = EnumFacing.values().getOrNull(faceIdx) ?: EnumFacing.NORTH
                        ScannerInstrumentNbt.writePreviewFacing(tag, facing)
                    }

                    Action.SET_PREVIEW_ROT.id -> {
                        val rot = buf.readVarInt().coerceIn(0, 3)
                        ScannerInstrumentNbt.writePreviewRot(tag, rot)
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
                java.util.function.BooleanSupplier { readBool(ScannerInstrumentNbt.TAG_EXPANDED) },
                BooleanConsumer { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_EXPANDED, v) } }
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
                java.util.function.BooleanSupplier { readBool(ScannerInstrumentNbt.TAG_SUBSTRUCTURE) },
                BooleanConsumer { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_SUBSTRUCTURE, v) } }
            )
        )
        syncManager.syncValue(
            "matchNbt",
            BooleanSyncValue(
                java.util.function.BooleanSupplier { readBool(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT) },
                BooleanConsumer { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_INCLUDE_TILE_NBT, v) } }
            )
        )
        syncManager.syncValue(
            "allowMirror",
            BooleanSyncValue(
                java.util.function.BooleanSupplier { readBool(ScannerInstrumentNbt.TAG_ALLOW_MIRROR) },
                BooleanConsumer { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_ALLOW_MIRROR, v) } }
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
                java.util.function.BooleanSupplier { readBool(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR) },
                BooleanConsumer { v -> withData { it.setBoolean(ScannerInstrumentNbt.TAG_PREVIEW_MIRROR, v) } }
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

    /** Helper for client code to send action packets to server. */
    fun callAction(syncManager: PanelSyncManager, action: Action, write: (PacketBuffer) -> Unit = {}) {
        syncManager.callSyncedAction(ACTION_KEY) { buf ->
            buf.writeVarInt(action.id)
            write(buf)
        }
    }
}
