package github.kasuminova.prototypemachinery.client.scanner

import com.cleanroommc.modularui.api.ITheme
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.drawable.IKey
import com.cleanroommc.modularui.api.widget.Interactable
import com.cleanroommc.modularui.api.widget.Interactable.Result
import com.cleanroommc.modularui.drawable.DynamicDrawable
import com.cleanroommc.modularui.utils.Alignment
import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.SliderWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.ToggleButton
import com.cleanroommc.modularui.widgets.layout.Flow
import com.cleanroommc.modularui.widgets.textfield.TextFieldWidget
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.PreviewBomEntry
import github.kasuminova.prototypemachinery.api.machine.structure.preview.PreviewBounds
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.client.gui.builder.UITextures
import github.kasuminova.prototypemachinery.client.preview.ui.widget.StructurePreview3DWidget
import github.kasuminova.prototypemachinery.common.scanner.ScannerInstrumentNbt
import github.kasuminova.prototypemachinery.common.scanner.ScannerInstrumentUi
import net.minecraft.block.properties.IProperty
import net.minecraft.block.state.IBlockState
import net.minecraft.client.Minecraft
import net.minecraft.init.Blocks
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly

/**
 * Client-side UI widgets for Scanner Instrument.
 */
@SideOnly(Side.CLIENT)
internal object ScannerInstrumentClientUi {

    private val textures = UITextures()

    private val EMPTY_PREVIEW_MODEL: StructurePreviewModel = StructurePreviewModel(
        blocks = emptyMap(),
        bounds = PreviewBounds(BlockPos(0, 0, 0), BlockPos(0, 0, 0)),
        bom = emptyList()
    )

    private fun computeBounds(positions: Collection<BlockPos>): PreviewBounds {
        if (positions.isEmpty()) {
            val zero = BlockPos(0, 0, 0)
            return PreviewBounds(zero, zero)
        }

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (pos in positions) {
            if (pos.x < minX) minX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.z < minZ) minZ = pos.z
            if (pos.x > maxX) maxX = pos.x
            if (pos.y > maxY) maxY = pos.y
            if (pos.z > maxZ) maxZ = pos.z
        }

        return PreviewBounds(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ))
    }

    private fun requirementFromState(state: IBlockState): ExactBlockStateRequirement? {
        val block = state.block
        val id: ResourceLocation = block.registryName ?: return null

        @Suppress("DEPRECATION")
        val meta = block.getMetaFromState(state)

        val props = LinkedHashMap<String, String>()
        for (rawProp in state.propertyKeys) {
            try {
                @Suppress("UNCHECKED_CAST")
                val prop = rawProp as IProperty<Comparable<Any>>
                val v = state.getValue(prop)
                @Suppress("UNCHECKED_CAST")
                props[prop.name] = prop.getName(v as Comparable<Any>)
            } catch (_: Throwable) {
                // ignore bad property values
            }
        }

        return ExactBlockStateRequirement(blockId = id, meta = meta, properties = props)
    }

    /**
     * Build a pure preview model from the current world selection.
     *
     * Notes:
     * - Runs on client UI thread; we keep it best-effort and cap work for huge selections.
     * - Skips air blocks, matching export behavior (includeAir = false).
     */
    private fun buildSelectionPreviewModel(tag: NBTTagCompound?): StructurePreviewModel {
        val o = tag?.getCompoundTagOrNull(ScannerInstrumentNbt.TAG_ORIGIN) ?: return EMPTY_PREVIEW_MODEL
        val c = tag.getCompoundTagOrNull(ScannerInstrumentNbt.TAG_CORNER) ?: return EMPTY_PREVIEW_MODEL

        val origin = BlockPos(o.getInteger("x"), o.getInteger("y"), o.getInteger("z"))
        val corner = BlockPos(c.getInteger("x"), c.getInteger("y"), c.getInteger("z"))

        val mc = Minecraft.getMinecraft()
        val world = mc.world ?: return EMPTY_PREVIEW_MODEL

        val minX = minOf(origin.x, corner.x)
        val minY = minOf(origin.y, corner.y)
        val minZ = minOf(origin.z, corner.z)
        val maxX = maxOf(origin.x, corner.x)
        val maxY = maxOf(origin.y, corner.y)
        val maxZ = maxOf(origin.z, corner.z)

        val sizeX = (maxX - minX + 1).coerceAtLeast(1)
        val sizeY = (maxY - minY + 1).coerceAtLeast(1)
        val sizeZ = (maxZ - minZ + 1).coerceAtLeast(1)
        val volume = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()

        // Hard cap to avoid freezing the UI when users select giant areas.
        // If exceeded, fall back to boundary-only sampling.
        val boundaryOnly = volume > 32768L

        val blocks = LinkedHashMap<BlockPos, github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement>(4096)
        val reqByKey = LinkedHashMap<String, github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement>(128)
        val counts = LinkedHashMap<String, Int>(128)

        fun isBoundary(x: Int, y: Int, z: Int): Boolean {
            return x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ
        }

        for (y in minY..maxY) {
            for (z in minZ..maxZ) {
                for (x in minX..maxX) {
                    if (boundaryOnly && !isBoundary(x, y, z)) continue

                    val wp = BlockPos(x, y, z)
                    val state = world.getBlockState(wp)
                    if (state.block == Blocks.AIR) continue

                    val req = requirementFromState(state) ?: continue
                    val rel = BlockPos(x - origin.x, y - origin.y, z - origin.z)
                    blocks[rel] = req

                    val key = req.stableKey()
                    if (!reqByKey.containsKey(key)) reqByKey[key] = req
                    counts[key] = (counts[key] ?: 0) + 1
                }
            }
        }

        val bounds = computeBounds(blocks.keys)
        val bom = counts.entries
            .sortedByDescending { it.value }
            .mapNotNull { (key, count) ->
                val req = reqByKey[key] ?: return@mapNotNull null
                PreviewBomEntry(req, count)
            }

        return StructurePreviewModel(blocks = blocks, bounds = bounds, bom = bom)
    }

    // --- Scanner GUI textures ---
    private fun scannerTex(path: String): IDrawable {
        return com.cleanroommc.modularui.drawable.UITexture.fullImage(
            net.minecraft.util.ResourceLocation("prototypemachinery", "gui/gui_scanner_instrument/$path")
        )
    }

    // Output buttons
    private val BTN_DELETE_N = scannerTex("delete_default")
    private val BTN_DELETE_H = scannerTex("delete_selected")
    private val BTN_DELETE_P = scannerTex("delete_pressed")

    private val BTN_RESET_N = scannerTex("reset_default")
    private val BTN_RESET_H = scannerTex("reset_selected")
    private val BTN_RESET_P = scannerTex("reset_pressed")

    private val BTN_OUTPUT_N = scannerTex("output_default")
    private val BTN_OUTPUT_H = scannerTex("output_selected")
    private val BTN_OUTPUT_P = scannerTex("output_pressed")

    // Origin buttons
    private val BTN_RESET_ORIGIN_N = scannerTex("button/reset_i_default")
    private val BTN_RESET_ORIGIN_H = scannerTex("button/reset_i_selected")
    private val BTN_RESET_ORIGIN_P = scannerTex("button/reset_i_pressed")

    private val BTN_SET_ORIGIN_N = scannerTex("button/set_default")
    private val BTN_SET_ORIGIN_H = scannerTex("button/set_selected")
    private val BTN_SET_ORIGIN_P = scannerTex("button/set_pressed")

    // Preview reset orientation
    private val BTN_RESET_ORIENT_N = scannerTex("button/reset_ii_default")
    private val BTN_RESET_ORIENT_H = scannerTex("button/reset_ii_selected")
    private val BTN_RESET_ORIENT_P = scannerTex("button/reset_ii_pressed")

    // Facing buttons (11x11)
    private fun facingTex(dir: String, variant: String): IDrawable {
        return scannerTex("preview/$dir/$variant")
    }

    private fun rotTex(dir: String, variant: String): IDrawable {
        return scannerTex("preview/rotate/$dir/$variant")
    }

    // gui_states skins
    private val INPUT_BG: IDrawable = textures.guiStatesSeparatedAdaptable(
        relPath = "gui/gui_states/input_box/box_expand",
        imageW = 8,
        imageH = 13,
        borderLeft = 3,
        borderTop = 6,
        borderRight = 4,
        borderBottom = 6
    )

    private fun sliderMXExpandNormal(slider: SliderWidget) {
        // Background: gui_states/slider/m/normal/lr_base_expand.png (17x13)
        val base = textures.guiStatesSeparatedAdaptable(
            relPath = "gui/gui_states/slider/m/normal/lr_base_expand",
            imageW = 17,
            imageH = 13,
            borderLeft = 7,
            borderTop = 0,
            borderRight = 9,
            borderBottom = 0
        )
        val handleNormal = textures.guiStatesFull("gui/gui_states/slider/m/lr_default")
        val handleHover = textures.guiStatesFull("gui/gui_states/slider/m/lr_selected")
        val handlePressed = textures.guiStatesFull("gui/gui_states/slider/m/lr_pressed")

        slider.background(base)
        slider.sliderTexture(
            DynamicDrawable {
                when {
                    slider.isDragging -> handlePressed
                    slider.isHovering -> handleHover
                    else -> handleNormal
                }
            }
        )
        slider.sliderSize(11, 11)
    }

    @JvmStatic
    fun addWidgets(root: Flow, tagProvider: () -> NBTTagCompound?, syncManager: PanelSyncManager) {
        // ---- 3D structure preview (x:10,y:12,w:171,h:203) ----
        // Note: this is a purely visual preview; all built-in interactions are disabled.
        val previewModel = buildSelectionPreviewModel(tagProvider())
        root.child(
            StructurePreview3DWidget(
                model = previewModel,
                wireframeProvider = { false },
                autoRotateProvider = { false },
                // Allow rotate/pan/zoom.
                inputEnabledProvider = { true },
                // Keep read-only: disable click picking / selection.
                clickPickEnabledProvider = { false },
                // Render a small compass (N/E/S/W) for orientation.
                compassEnabledProvider = { true }
            )
                .pos(10, 12)
                .size(171, 203)
                .addTooltipLine("结构预览（只读）")
        )

        // ---- lang/id input boxes ----
        root.child(
            TextFieldWidget()
                .syncHandler("langName", 0)
                .background(INPUT_BG)
                .hoverBackground(INPUT_BG)
                .addTooltipLine("本地化名称：导出结构时的显示名（可留空）")
                .pos(215, 17)
                .size(153, 13)
        )
        root.child(
            TextFieldWidget()
                .syncHandler("structureId", 0)
                .background(INPUT_BG)
                .hoverBackground(INPUT_BG)
                .addTooltipLine("结构 ID：用于保存文件名/注册（会自动 sanitize）")
                .pos(215, 31)
                .size(153, 13)
        )

        // ---- expanded setup ----
        root.child(
            smallSwitchToggle()
                .addTooltipLine("是否启用扩展结构（按数量/间距生成重复切片）")
                .syncHandler("expandedEnabled", 0)
                // expanded_setup: X:197 Y:50; expanded_o_f: X:19 Y:3
                .pos(216, 53)
                .size(20, 13)
        )

        root.child(
            TextFieldWidget()
                .syncHandler("expandedQuantityStr", 0)
                .setNumbersLong { it.coerceAtLeast(1L) }
                .background(INPUT_BG)
                .hoverBackground(INPUT_BG)
                .addTooltipLine("扩展数量：切片重复次数（最小 1）")
                // expanded_quantity: X:19 Y:17 W:68 H:13
                .pos(216, 67)
                .size(68, 13)
        )
        root.child(
            SliderWidget()
                .syncHandler("expandedQuantity", 0)
                .bounds(1.0, 256.0)
                .apply { sliderMXExpandNormal(this) }
                // expanded_quantity_preview: X:41 Y:3 W:131 H:13
                .pos(238, 53)
                .size(131, 13)
        )

        root.child(
            TextFieldWidget()
                .syncHandler("expandedSpacingStr", 0)
                .setNumbersLong { it.coerceAtLeast(0L) }
                .background(INPUT_BG)
                .hoverBackground(INPUT_BG)
                .addTooltipLine("扩展间距：每段切片之间的间隔（最小 0）")
                // expanded_spacing: X:103 Y:17 W:68 H:13
                .pos(300, 67)
                .size(68, 13)
        )

        // ---- structure setup small switches ----
        root.child(
            smallSwitchToggle()
                .addTooltipLine("子结构模式：导出时标记为子结构")
                .syncHandler("substructure", 0)
                .pos(215, 88)
                .size(20, 13)
        )
        root.child(
            smallSwitchToggle()
                .addTooltipLine("匹配 NBT：导出时包含 TileEntity NBT 约束")
                .syncHandler("matchNbt", 0)
                .pos(263, 88)
                .size(20, 13)
        )
        root.child(
            smallSwitchToggle()
                .addTooltipLine("允许镜像：结构可在镜像后匹配")
                .syncHandler("allowMirror", 0)
                .pos(306, 88)
                .size(20, 13)
        )

        // ---- origin edit fields ----
        root.child(
            TextFieldWidget()
                .syncHandler("originEditX", 0)
                .setNumbersLong { it }
                .background(INPUT_BG)
                .hoverBackground(INPUT_BG)
                // NOTE: 与底图中的图标/留白对齐：向右微调。
                .addTooltipLine("原点 X：可从预览中选取/或手动输入")
                .pos(219, 110)
                .size(69, 13)
        )
        root.child(
            TextFieldWidget()
                .syncHandler("originEditY", 0)
                .setNumbersLong { it }
                .background(INPUT_BG)
                .hoverBackground(INPUT_BG)
                .addTooltipLine("原点 Y：可从预览中选取/或手动输入")
                .pos(219, 124)
                .size(69, 13)
        )
        root.child(
            TextFieldWidget()
                .syncHandler("originEditZ", 0)
                .setNumbersLong { it }
                .background(INPUT_BG)
                .hoverBackground(INPUT_BG)
                .addTooltipLine("原点 Z：可从预览中选取/或手动输入")
                .pos(219, 138)
                .size(69, 13)
        )

        // reset origin (client: just refocuses values from current origin)
        root.child(
            TriStateButton(BTN_RESET_ORIGIN_N, BTN_RESET_ORIGIN_H, BTN_RESET_ORIGIN_P)
                .pos(200, 154)
                .size(39, 13)
                .addTooltipLine("重置原点输入：用当前原点坐标填充 X/Y/Z")
                .onMousePressed { mouseButton: Int ->
                    if (mouseButton != 0) return@onMousePressed false
                    // We can't directly set TextFieldWidget text reliably here;
                    // instead, set originEdit to current origin via server action.
                    val tag = tagProvider() ?: return@onMousePressed true
                    val origin = tag.getCompoundTagOrNull(ScannerInstrumentNbt.TAG_ORIGIN)
                    if (origin != null) {
                        ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.SET_ORIGIN_TO) { p ->
                            p.writeInt(origin.getInteger("x"))
                            p.writeInt(origin.getInteger("y"))
                            p.writeInt(origin.getInteger("z"))
                        }
                    }
                    true
                }
        )

        // set origin from edit buffer
        root.child(
            TriStateButton(BTN_SET_ORIGIN_N, BTN_SET_ORIGIN_H, BTN_SET_ORIGIN_P)
                .pos(242, 154)
                .size(39, 13)
                .addTooltipLine("设定原点：把 X/Y/Z 输入写入为结构原点")
                .onMousePressed { mouseButton: Int ->
                    if (mouseButton != 0) return@onMousePressed false
                    ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.SET_ORIGIN_FROM_EDIT)
                    true
                }
        )

        // size preview text
        root.child(
            TextWidget(IKey.dynamic {
                val tag = tagProvider() ?: return@dynamic "X:---- Y:---- Z:----"
                val o = tag.getCompoundTagOrNull(ScannerInstrumentNbt.TAG_ORIGIN)
                val c = tag.getCompoundTagOrNull(ScannerInstrumentNbt.TAG_CORNER)
                if (o == null || c == null) return@dynamic "X:---- Y:---- Z:----"
                val ox = o.getInteger("x")
                val oy = o.getInteger("y")
                val oz = o.getInteger("z")
                val cx = c.getInteger("x")
                val cy = c.getInteger("y")
                val cz = c.getInteger("z")
                val sx = kotlin.math.abs(cx - ox) + 1
                val sy = kotlin.math.abs(cy - oy) + 1
                val sz = kotlin.math.abs(cz - oz) + 1
                "X:$sx Y:$sy Z:$sz"
            })
                // origin_set_size_preview: size_preview Y:67 -> 65
                .pos(221, 172)
                .size(64, 8)
                .alignment(Alignment.CenterLeft)
        )

        // ---- preview orientation group ----
        root.child(
            TriStateButton(BTN_RESET_ORIENT_N, BTN_RESET_ORIENT_H, BTN_RESET_ORIENT_P)
                .pos(306, 132)
                .size(30, 13)
                .addTooltipLine("重置预览方向：朝向/旋转/镜像恢复默认")
                .onMousePressed { mouseButton: Int ->
                    if (mouseButton != 0) return@onMousePressed false
                    ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.RESET_PREVIEW_ORIENTATION)
                    true
                }
        )

        root.child(
            chooseSwitchToggle()
                .addTooltipLine("镜像预览：仅影响预览显示，不会修改已选坐标")
                .syncHandler("previewMirror", 0)
                .pos(356, 133)
                .size(11, 11)
        )

        // Facing selection buttons
        root.child(FacingButton(EnumFacing.UP, "y_add", tagProvider, syncManager).pos(290 + 12, 149 + 0).size(11, 11))
        root.child(FacingButton(EnumFacing.DOWN, "y_sub", tagProvider, syncManager).pos(290 + 12, 149 + 24).size(11, 11))
        root.child(FacingButton(EnumFacing.EAST, "x_add", tagProvider, syncManager).pos(290 + 0, 149 + 12).size(11, 11))
        root.child(FacingButton(EnumFacing.WEST, "x_sub", tagProvider, syncManager).pos(290 + 24, 149 + 12).size(11, 11))
        root.child(FacingButton(EnumFacing.NORTH, "z_add", tagProvider, syncManager).pos(290 + 12, 149 + 12).size(11, 11))
        root.child(FacingButton(EnumFacing.SOUTH, "z_sub", tagProvider, syncManager).pos(290 + 0, 149 + 24).size(11, 11))

        // Rotation selection buttons (0..3)
        root.child(RotationButton(0, "y_add", tagProvider, syncManager).pos(333 + 12, 149 + 0).size(11, 11))
        root.child(RotationButton(1, "y_sub", tagProvider, syncManager).pos(333 + 12, 149 + 24).size(11, 11))
        root.child(RotationButton(2, "x_sub", tagProvider, syncManager).pos(333 + 0, 149 + 12).size(11, 11))
        root.child(RotationButton(3, "x_add", tagProvider, syncManager).pos(333 + 24, 149 + 12).size(11, 11))

        // ---- output setup buttons ----
        root.child(
            TriStateButton(BTN_DELETE_N, BTN_DELETE_H, BTN_DELETE_P)
                .pos(197, 191)
                .size(21, 22)
                .addTooltipLine("清空：清除已选择的 origin/corner")
                .onMousePressed { mouseButton: Int ->
                    if (mouseButton != 0) return@onMousePressed false
                    ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.CLEAR)
                    true
                }
        )

        root.child(
            TriStateButton(BTN_RESET_N, BTN_RESET_H, BTN_RESET_P)
                .pos(219, 191)
                .size(21, 22)
                .addTooltipLine("初始化：重置界面字段并清空选择")
                .onMousePressed { mouseButton: Int ->
                    if (mouseButton != 0) return@onMousePressed false
                    ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.RESET)
                    true
                }
        )

        root.child(
            TriStateButton(BTN_OUTPUT_N, BTN_OUTPUT_H, BTN_OUTPUT_P)
                .pos(241, 191)
                .size(130, 22)
                .addTooltipLine("导出：将当前选择导出为结构 JSON（写入 config/.../scanned）")
                .onMousePressed { mouseButton: Int ->
                    if (mouseButton != 0) return@onMousePressed false
                    ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.EXPORT)
                    true
                }
        )
    }

    private class TriStateButton(
        private val normal: IDrawable,
        private val hover: IDrawable,
        private val pressed: IDrawable,
    ) : ButtonWidget<TriStateButton>() {

        private var pressedNow: Boolean = false

        override fun getCurrentBackground(theme: ITheme, widgetTheme: com.cleanroommc.modularui.theme.WidgetThemeEntry<*>): IDrawable? {
            return when {
                pressedNow -> pressed
                isHovering -> hover
                else -> normal
            }
        }

        override fun onMousePressed(mouseButton: Int): Result {
            if (mouseButton == 0) pressedNow = true
            return super.onMousePressed(mouseButton)
        }

        override fun onMouseRelease(mouseButton: Int): Boolean {
            if (mouseButton == 0) pressedNow = false
            return super.onMouseRelease(mouseButton)
        }
    }

    private fun chooseSwitchToggle(): ToggleButton {
        val off = textures.guiStatesFull("gui/gui_states/choose_switch/normal/off")
        val offSel = textures.guiStatesFull("gui/gui_states/choose_switch/normal/off_selected")
        val on = textures.guiStatesFull("gui/gui_states/choose_switch/normal/on")
        val onSel = textures.guiStatesFull("gui/gui_states/choose_switch/normal/on_selected")
        return ToggleButton()
            .background(false, off)
            .hoverBackground(false, offSel)
            .background(true, on)
            .hoverBackground(true, onSel)
    }

    private fun smallSwitchToggle(): ToggleButton {
        val off = textures.guiStatesFull("gui/gui_states/switch/s_normal/off")
        val offSel = textures.guiStatesFull("gui/gui_states/switch/s_normal/off_selected")
        val on = textures.guiStatesFull("gui/gui_states/switch/s_normal/on")
        val onSel = textures.guiStatesFull("gui/gui_states/switch/s_normal/on_selected")
        return ToggleButton()
            .background(false, off)
            .hoverBackground(false, offSel)
            .background(true, on)
            .hoverBackground(true, onSel)
    }

    private class FacingButton(
        private val facing: EnumFacing,
        private val texDir: String,
        private val tagProvider: () -> NBTTagCompound?,
        private val syncManager: PanelSyncManager,
    ) : ButtonWidget<FacingButton>() {

        override fun getCurrentBackground(theme: ITheme, widgetTheme: com.cleanroommc.modularui.theme.WidgetThemeEntry<*>): IDrawable? {
            val tag = tagProvider()
            val selected = tag?.getInteger(ScannerInstrumentNbt.TAG_PREVIEW_FACING) == facing.index
            val variant = when {
                selected || isHovering -> "selected"
                else -> "default"
            }
            return facingTex(texDir, variant)
        }

        init {
            addTooltipLine(
                when (facing) {
                    EnumFacing.UP -> "预览朝向：顶 (UP)"
                    EnumFacing.DOWN -> "预览朝向：底 (DOWN)"
                    EnumFacing.EAST -> "预览朝向：东 (EAST)"
                    EnumFacing.WEST -> "预览朝向：西 (WEST)"
                    EnumFacing.NORTH -> "预览朝向：北 (NORTH)"
                    EnumFacing.SOUTH -> "预览朝向：南 (SOUTH)"
                }
            )
            onMousePressed { btn ->
                if (btn != 0) return@onMousePressed false
                ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.SET_PREVIEW_FACING) { p ->
                    p.writeVarInt(facing.index)
                }
                true
            }
        }
    }

    private class RotationButton(
        private val rot: Int,
        private val texDir: String,
        private val tagProvider: () -> NBTTagCompound?,
        private val syncManager: PanelSyncManager,
    ) : ButtonWidget<RotationButton>() {

        override fun getCurrentBackground(theme: ITheme, widgetTheme: com.cleanroommc.modularui.theme.WidgetThemeEntry<*>): IDrawable? {
            val tag = tagProvider()
            val selected = (tag?.getInteger(ScannerInstrumentNbt.TAG_PREVIEW_ROT) ?: 0) == rot
            val variant = when {
                selected || isHovering -> "selected"
                else -> "default"
            }
            return rotTex(texDir, variant)
        }

        init {
            addTooltipLine(
                when (rot) {
                    0 -> "预览旋转：0°"
                    1 -> "预览旋转：90°"
                    2 -> "预览旋转：180°"
                    3 -> "预览旋转：270°"
                    else -> "预览旋转"
                }
            )
            onMousePressed { btn ->
                if (btn != 0) return@onMousePressed false
                ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.SET_PREVIEW_ROT) { p ->
                    p.writeVarInt(rot)
                }
                true
            }
        }
    }

    /**
     * A transparent click-catcher over the preview area.
     *
     * For now, we approximate "click in preview to select block" by using the current crosshair target
     * in the world, then setting it as origin.
     */
    private class PreviewClickWidget(
        private val tagProvider: () -> NBTTagCompound?,
        private val syncManager: PanelSyncManager,
    ) : com.cleanroommc.modularui.widget.Widget<PreviewClickWidget>(), Interactable {

        override fun onMousePressed(mouseButton: Int): Result {
            if (mouseButton != 0) return Result.IGNORE
            if (!isHovering) return Result.IGNORE

            val mc = Minecraft.getMinecraft()
            val hit = mc.objectMouseOver ?: return Result.SUCCESS
            val pos = hit.blockPos ?: return Result.SUCCESS

            // Only accept within current selection if both points exist.
            val tag = tagProvider()
            if (tag != null) {
                val o = tag.getCompoundTagOrNull(ScannerInstrumentNbt.TAG_ORIGIN)
                val c = tag.getCompoundTagOrNull(ScannerInstrumentNbt.TAG_CORNER)
                if (o != null && c != null) {
                    val min = BlockPos(
                        minOf(o.getInteger("x"), c.getInteger("x")),
                        minOf(o.getInteger("y"), c.getInteger("y")),
                        minOf(o.getInteger("z"), c.getInteger("z")),
                    )
                    val max = BlockPos(
                        maxOf(o.getInteger("x"), c.getInteger("x")),
                        maxOf(o.getInteger("y"), c.getInteger("y")),
                        maxOf(o.getInteger("z"), c.getInteger("z")),
                    )
                    if (pos.x !in min.x..max.x || pos.y !in min.y..max.y || pos.z !in min.z..max.z) {
                        return Result.SUCCESS
                    }
                }
            }

            ScannerInstrumentUi.callAction(syncManager, ScannerInstrumentUi.Action.SET_ORIGIN_TO) { p ->
                p.writeInt(pos.x)
                p.writeInt(pos.y)
                p.writeInt(pos.z)
            }
            Interactable.playButtonClickSound()
            return Result.SUCCESS
        }
    }

    private fun NBTTagCompound.getCompoundTagOrNull(key: String): NBTTagCompound? {
        return if (hasKey(key, 10)) getCompoundTag(key) else null
    }
}
