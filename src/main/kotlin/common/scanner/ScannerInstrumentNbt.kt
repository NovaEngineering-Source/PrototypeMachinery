package github.kasuminova.prototypemachinery.common.scanner

import github.kasuminova.prototypemachinery.common.scanner.ScannerInstrumentNbt.TAG_ORIGIN
import github.kasuminova.prototypemachinery.common.structure.tools.StructureExportUtil
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.math.BlockPos

/**
 * NBT schema for Scanner Instrument.
 *
 * 统一管理 ScannerInstrument 物品上的 NBT 字段，避免 UI / 物品交互 / 渲染三处各写一套字符串常量。
 */
internal object ScannerInstrumentNbt {

    // Root tag under ItemStack.tagCompound
    const val TAG_ROOT: String = "pm_scanner"

    // Selection endpoints (world positions)
    const val TAG_ORIGIN: String = "origin"
    const val TAG_CORNER: String = "corner"

    /** Export/template origin (world position). If absent, fall back to [TAG_ORIGIN]. */
    const val TAG_EXPORT_ORIGIN: String = "exportOrigin"

    /** When true, exportOrigin may be auto-derived from a controller in selection. */
    const val TAG_EXPORT_ORIGIN_AUTO: String = "exportOriginAuto"

    /** When true, previewFacing/previewRot may be auto-derived from controller orientation. */
    const val TAG_PREVIEW_AUTO: String = "previewAuto"

    // Export metadata
    const val TAG_STRUCTURE_ID: String = "structureId"
    const val TAG_LANG_NAME: String = "langName"

    /** Whether to include shallow TileEntity root NBT constraints in exported pattern elements. */
    const val TAG_INCLUDE_TILE_NBT: String = "includeTileNbt"

    // Extra config (currently UI-facing; export may ignore some of them)
    const val TAG_EXPANDED: String = "expanded"
    const val TAG_EXPANDED_QUANTITY: String = "expandedQuantity"
    const val TAG_EXPANDED_SPACING: String = "expandedSpacing"

    const val TAG_SUBSTRUCTURE: String = "substructure"
    const val TAG_ALLOW_MIRROR: String = "allowMirror"

    // Origin edit buffer (UI input fields)
    const val TAG_ORIGIN_EDIT_X: String = "originEditX"
    const val TAG_ORIGIN_EDIT_Y: String = "originEditY"
    const val TAG_ORIGIN_EDIT_Z: String = "originEditZ"

    // Preview config (UI-only)
    const val TAG_PREVIEW_FACING: String = "previewFacing" // EnumFacing.getIndex()
    const val TAG_PREVIEW_ROT: String = "previewRot" // 0..3 => 0/90/180/270
    const val TAG_PREVIEW_MIRROR: String = "previewMirror"

    fun getOrCreateData(stack: ItemStack): NBTTagCompound {
        val root = stack.tagCompound ?: NBTTagCompound().also { stack.tagCompound = it }
        return if (root.hasKey(TAG_ROOT, 10)) {
            root.getCompoundTag(TAG_ROOT)
        } else {
            val sub = NBTTagCompound()
            root.setTag(TAG_ROOT, sub)
            sub
        }
    }

    fun getDataOrNull(stack: ItemStack): NBTTagCompound? {
        val root = stack.tagCompound ?: return null
        if (!root.hasKey(TAG_ROOT, 10)) return null
        return root.getCompoundTag(TAG_ROOT)
    }

    fun ensureDefaults(stack: ItemStack, data: NBTTagCompound) {
        // Ensure structureId is never empty (helps export UX).
        if (!data.hasKey(TAG_STRUCTURE_ID, 8) || data.getString(TAG_STRUCTURE_ID).isBlank()) {
            data.setString(TAG_STRUCTURE_ID, StructureExportUtil.sanitizeId(stack.displayName))
        }

        // Default expanded values (UI sliders)
        if (!data.hasKey(TAG_EXPANDED_QUANTITY, 3)) data.setInteger(TAG_EXPANDED_QUANTITY, 1)
        if (!data.hasKey(TAG_EXPANDED_SPACING, 3)) data.setInteger(TAG_EXPANDED_SPACING, 0)

        // Default preview orientation
        if (!data.hasKey(TAG_PREVIEW_FACING, 3)) data.setInteger(TAG_PREVIEW_FACING, EnumFacing.NORTH.index)
        if (!data.hasKey(TAG_PREVIEW_ROT, 3)) data.setInteger(TAG_PREVIEW_ROT, 0)

        // Auto flags (default ON)
        if (!data.hasKey(TAG_EXPORT_ORIGIN_AUTO, 1)) data.setBoolean(TAG_EXPORT_ORIGIN_AUTO, true)
        if (!data.hasKey(TAG_PREVIEW_AUTO, 1)) data.setBoolean(TAG_PREVIEW_AUTO, true)
    }

    fun readOrigin(data: NBTTagCompound): BlockPos? = data.getBlockPosOrNull(TAG_ORIGIN)

    fun readCorner(data: NBTTagCompound): BlockPos? = data.getBlockPosOrNull(TAG_CORNER)

    fun writeOrigin(data: NBTTagCompound, pos: BlockPos) {
        data.putBlockPos(TAG_ORIGIN, pos)
        // Keep edit buffer aligned by default
        data.setInteger(TAG_ORIGIN_EDIT_X, pos.x)
        data.setInteger(TAG_ORIGIN_EDIT_Y, pos.y)
        data.setInteger(TAG_ORIGIN_EDIT_Z, pos.z)
    }

    fun readExportOrigin(data: NBTTagCompound): BlockPos? = data.getBlockPosOrNull(TAG_EXPORT_ORIGIN)

    fun writeExportOrigin(data: NBTTagCompound, pos: BlockPos) {
        data.putBlockPos(TAG_EXPORT_ORIGIN, pos)
        // Keep edit buffer aligned by default
        data.setInteger(TAG_ORIGIN_EDIT_X, pos.x)
        data.setInteger(TAG_ORIGIN_EDIT_Y, pos.y)
        data.setInteger(TAG_ORIGIN_EDIT_Z, pos.z)
    }

    /** Effective export origin used for relPos calculation. */
    fun readEffectiveExportOrigin(data: NBTTagCompound): BlockPos? = readExportOrigin(data) ?: readOrigin(data)

    fun writeCorner(data: NBTTagCompound, pos: BlockPos) {
        data.putBlockPos(TAG_CORNER, pos)
    }

    fun clearSelection(data: NBTTagCompound) {
        data.removeTag(TAG_ORIGIN)
        data.removeTag(TAG_CORNER)
        data.removeTag(TAG_EXPORT_ORIGIN)
    }

    fun readPreviewFacing(data: NBTTagCompound): EnumFacing {
        val idx = data.getInteger(TAG_PREVIEW_FACING)
        val values = EnumFacing.values()
        return values.getOrNull(idx) ?: EnumFacing.NORTH
    }

    fun writePreviewFacing(data: NBTTagCompound, facing: EnumFacing) {
        data.setInteger(TAG_PREVIEW_FACING, facing.index)
    }

    fun readPreviewRot(data: NBTTagCompound): Int {
        return data.getInteger(TAG_PREVIEW_ROT).coerceIn(0, 3)
    }

    fun writePreviewRot(data: NBTTagCompound, rot: Int) {
        data.setInteger(TAG_PREVIEW_ROT, rot.coerceIn(0, 3))
    }

    /**
     * Builds a world→template rotation function based on current preview settings.
     *
     * Semantics:
     * - previewFacing/previewRot describe the *world* orientation that should become (NORTH, UP) in the exported/template space.
     * - returned function maps any world-facing into the corresponding template-facing.
     */
    fun worldToTemplateRotationFromPreview(data: NBTTagCompound): (EnumFacing) -> EnumFacing {
        val frontWorld = readPreviewFacing(data)
        val rot = readPreviewRot(data)
        val o = github.kasuminova.prototypemachinery.common.util.TwistMath.toStructureOrientation(frontWorld, rot)

        // Define template basis in world space: (NORTH, UP, EAST) → (front, top, right)
        val templateToWorld = java.util.EnumMap<EnumFacing, EnumFacing>(EnumFacing::class.java)
        templateToWorld[EnumFacing.NORTH] = o.front
        templateToWorld[EnumFacing.UP] = o.top
        templateToWorld[EnumFacing.EAST] = o.right
        templateToWorld[EnumFacing.SOUTH] = o.front.opposite
        templateToWorld[EnumFacing.DOWN] = o.top.opposite
        templateToWorld[EnumFacing.WEST] = o.right.opposite

        // Invert to get world→template.
        val worldToTemplate = java.util.EnumMap<EnumFacing, EnumFacing>(EnumFacing::class.java)
        for ((k, v) in templateToWorld) {
            worldToTemplate[v] = k
        }
        return { worldFacing -> worldToTemplate[worldFacing] ?: worldFacing }
    }

    private fun NBTTagCompound.putBlockPos(key: String, pos: BlockPos) {
        val tag = NBTTagCompound()
        tag.setInteger("x", pos.x)
        tag.setInteger("y", pos.y)
        tag.setInteger("z", pos.z)
        setTag(key, tag)
    }

    private fun NBTTagCompound.getBlockPosOrNull(key: String): BlockPos? {
        if (!hasKey(key, 10)) return null
        val tag = getCompoundTag(key)
        return BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z"))
    }
}
