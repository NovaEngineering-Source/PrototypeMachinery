package github.kasuminova.prototypemachinery.client.preview.ui

import com.cleanroommc.modularui.api.widget.IWidget
import com.cleanroommc.modularui.drawable.Rectangle
import com.cleanroommc.modularui.drawable.UITexture
import com.cleanroommc.modularui.screen.GuiScreenWrapper
import com.cleanroommc.modularui.screen.ModularPanel
import com.cleanroommc.modularui.screen.ModularScreen
import com.cleanroommc.modularui.screen.UISettings
import com.cleanroommc.modularui.utils.Color
import com.cleanroommc.modularui.value.BoolValue
import com.cleanroommc.modularui.value.IntValue
import com.cleanroommc.modularui.value.StringValue
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.ListWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.layout.Flow
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.LiteralRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.UnknownRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewBomLine
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewStats
import github.kasuminova.prototypemachinery.client.preview.scan.StructurePreviewWorldScanCache
import github.kasuminova.prototypemachinery.client.preview.ui.widget.StructurePreview3DWidget
import github.kasuminova.prototypemachinery.client.util.ClientNextTick
import github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder
import net.minecraft.client.Minecraft
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation

/**
 * Minimal read-only ModularUI screen for structure preview.
 *
 * First milestone:
 * - Openable from a client command
 * - Shows BOM + aggregated counters
 * - Optional client-side world scan (host-gated; JEI/read-only can keep it disabled)
 * - Optional embedded 3D view (JEI-friendly; does not require a world)
 */
internal object StructurePreviewUiScreen {

    private val SELECT_BG = Rectangle().color(Color.withAlpha(Color.CYAN.main, 0.22f))

    private val PANEL_BG = UITexture.builder()
        .location("prototypemachinery", "gui/gui_structure_preview_a")
        .imageSize(256, 256)
        .subAreaXYWH(0, 0, 184, 220)
        .build()

    private fun subTex(x: Int, y: Int, w: Int, h: Int): UITexture {
        return UITexture.builder()
            .location("prototypemachinery", "gui/gui_structure_preview_a")
            .imageSize(256, 256)
            .subAreaXYWH(x, y, w, h)
            .build()
    }

    private class StructurePreviewUiState(
        val search: StringValue = StringValue(""),
        val showOk: BoolValue = BoolValue(true),
        val showMismatch: BoolValue = BoolValue(true),
        val showUnloaded: BoolValue = BoolValue(true),
        val showUnknown: BoolValue = BoolValue(true),
        val sortMode: IntValue = IntValue(0),
        /** 是否显示 3D 视图（默认由 host 决定，JEI 建议默认开启）。 */
        val show3d: BoolValue = BoolValue(false),
        /** 是否启用世界扫描（默认关闭，避免 JEI/只读宿主依赖 world）。 */
        val enableScan: BoolValue = BoolValue(false),

        // Foldable menus
        val menuView: BoolValue = BoolValue(true),
        val menuFilter: BoolValue = BoolValue(true),
        val menuScan: BoolValue = BoolValue(false),
        val menuSelected: BoolValue = BoolValue(true)
    ) {
        var lastShownCount: Int = 0
        var selectedKey: String? = null

        var stats: StructurePreviewStats = StructurePreviewStats.EMPTY
        var rawBomLines: List<StructurePreviewBomLine> = emptyList()

        fun reset() {
            search.stringValue = ""
            showOk.boolValue = true
            showMismatch.boolValue = true
            showUnloaded.boolValue = true
            showUnknown.boolValue = true
            sortMode.intValue = 0
            show3d.boolValue = false
            enableScan.boolValue = false

            menuView.boolValue = true
            menuFilter.boolValue = true
            menuScan.boolValue = false
            menuSelected.boolValue = true
            selectedKey = null
        }

        fun filterAndSort(lines: List<StructurePreviewBomLine>): List<StructurePreviewBomLine> {
            val q = search.stringValue.trim().lowercase()
            val filtered = lines.asSequence()
                .filter { line ->
                    if (q.isBlank()) return@filter true
                    val key = line.requirement.stableKey().lowercase()
                    val short = formatRequirementShort(line.requirement).lowercase()
                    key.contains(q) || short.contains(q)
                }
                .filter { line ->
                    val ok = line.mismatchCount == 0 && line.unloaded == 0 && line.unknown == 0
                    val matchOk = showOk.boolValue && ok
                    val matchMismatch = showMismatch.boolValue && line.mismatchCount > 0
                    val matchUnloaded = showUnloaded.boolValue && line.unloaded > 0
                    val matchUnknown = showUnknown.boolValue && line.unknown > 0
                    matchOk || matchMismatch || matchUnloaded || matchUnknown
                }
                .toList()

            // sortMode:
            // 0 = by issues (mismatch/unloaded/unknown) then count
            // 1 = by required count desc
            // 2 = by key asc
            return when (sortMode.intValue) {
                1 -> filtered.sortedWith(
                    compareByDescending<StructurePreviewBomLine> { it.requiredCount }
                        .thenByDescending { it.mismatchCount }
                        .thenByDescending { it.unloaded }
                        .thenByDescending { it.unknown }
                        .thenBy { it.key }
                )

                2 -> filtered.sortedWith(compareBy<StructurePreviewBomLine> { it.key })
                else -> filtered.sortedWith(
                    compareByDescending<StructurePreviewBomLine> { it.mismatchCount }
                        .thenByDescending { it.unloaded }
                        .thenByDescending { it.unknown }
                        .thenByDescending { it.requiredCount }
                        .thenBy { it.key }
                )
            }
        }
    }

    /** Button helper for Kotlin (avoid dealing with W extends ButtonWidget<W>). */
    private class UiButton : ButtonWidget<UiButton>()

    private class UiHotspot : com.cleanroommc.modularui.widget.Widget<UiHotspot>()

    /**
     * Kotlin helper to avoid dealing with ModularUI's self-referential generics
     * (ListWidget<I, W extends ListWidget<I, W>>) at call sites.
     */
    private class WidgetList : ListWidget<IWidget, WidgetList>()

    fun open(
        structureId: String,
        sliceCountOverride: Int?,
        host: StructurePreviewUiHostConfig = StructurePreviewUiHostConfig.standalone()
    ) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return

        val structure = PrototypeMachineryAPI.structureRegistry.get(structureId)
        if (structure == null) {
            player.sendMessage(TextComponentTranslation("pm.preview.unknown_structure", structureId))
            return
        }

        val model: StructurePreviewModel = StructurePreviewBuilder.build(
            structure = structure,
            options = StructurePreviewBuilder.Options(
                sliceCountSelector = { sliceLike ->
                    sliceCountOverride ?: sliceLike.minCount
                }
            )
        )

        val state = StructurePreviewUiState()
        state.show3d.boolValue = host.defaultTo3DView
        val panel = buildPanel(
            state = state,
            model = model,
            structureId = structureId,
            host = host
        )
        val screen = ModularScreen("prototypemachinery", panel)
        // IMPORTANT: initialise ModularUI context settings before the screen is displayed.
        // JEI queries ModularUI context on GuiOpenEvent, which happens before initGui/onResize.
        // Without settings, ModularGuiContext#getUISettings throws "The screen is not yet initialised!".
        screen.context.setSettings(UISettings())

        // NOTE: When executed from chat (client command), the chat screen will close itself
        // after sending/handling the command, potentially overriding a screen opened immediately.
        // Minecraft#addScheduledTask may run immediately when called on the main thread, so we
        // use a true "next tick" queue here.
        ClientNextTick.enqueue {
            mc.displayGuiScreen(GuiScreenWrapper(screen))
        }
    }

    private fun buildPanel(
        state: StructurePreviewUiState,
        model: StructurePreviewModel,
        structureId: String,
        host: StructurePreviewUiHostConfig
    ): ModularPanel {
        val panel = ModularPanel.defaultPanel("structure_preview_ui")
            .size(184, 220)
            .background(PANEL_BG)

        panel.child(ButtonWidget.panelCloseButton())

        // Root container (fixed-position children).
        val root = Column()
            .pos(0, 0)
            .size(184, 220)

        val mc = Minecraft.getMinecraft()

        // --- Bottom layer: render panel (can be covered by all other elements) ---
        var statusSnapshot: Map<BlockPos, StructurePreviewEntryStatus> = emptyMap()
        val issuesOnly = BoolValue(false)
        val autoRotate = BoolValue(false)

        val sizeY = (model.bounds.max.y - model.bounds.min.y + 1).coerceAtLeast(1)
        val sliceY = IntValue((sizeY / 2).coerceIn(0, sizeY - 1))

        val view3d = StructurePreview3DWidget(
            model = model,
            statusProvider = { statusSnapshot },
            issuesOnlyProvider = { issuesOnly.boolValue },
            sliceModeProvider = { !state.show3d.boolValue },
            sliceYProvider = { sliceY.intValue },
            autoRotateProvider = { autoRotate.boolValue }
        )
            .pos(0, 0)
            .size(171, 205)

        root.child(view3d)

        // --- Title blocks (overlay) ---
        root.child(subTex(0, 224, 115, 18).asWidget().pos(8, 10).size(115, 18))
        root.child(
            TextWidget(structureId)
                .pos(11, 15)
                .color(Color.WHITE.main)
                .shadow(false)
                .maxWidth(113)
        )

        // Right-side buttons are icon-only in the background texture.
        fun iconHotspot(
            x: Int,
            y: Int,
            texX: Int,
            texY: Int,
            tooltip: String = "",
            enabledProvider: (() -> Boolean)? = null,
            onClick: () -> Unit = { }
        ): UiButton {
            val btn = UiButton()
                .pos(x, y)
                .size(17, 17)
                .background(
                    UITexture.builder()
                        .location(ResourceLocation("prototypemachinery", "gui/gui_structure_preview_a"))
                        .imageSize(256, 256)
                        .subAreaXYWH(texX, texY, 17, 17)
                        .build()
                )
                .tooltipStatic { it.addLine(tooltip) }
                .onMousePressed { mouseButton ->
                    if (mouseButton != 0) return@onMousePressed false
                    onClick()
                    true
                }

            if (enabledProvider != null) {

            }
            return btn
        }

        // Reset camera button
        root.child(
            iconHotspot(123, 10, 239, 3, "Reset camera") {
                view3d.resetView()
            }
        )

        // Toggle issues only button
        root.child(
            iconHotspot(141, 10, 239, 21, "Toggle: show issues only") {
                issuesOnly.boolValue = !issuesOnly.boolValue
            }
        )

        // Right information area (placeholder for future data-driven details)
        root.child(
            iconHotspot(159, 10, texX = 239, texY = 39)
                .tooltipDynamic { tt ->
                    tt.addLine("Scan: ${if (state.enableScan.boolValue) "ON" else "OFF"}")
                    tt.addLine("Issues only: ${issuesOnly.boolValue}")
                    tt.addLine("Mode: ${if (state.show3d.boolValue) "3D" else "Slice"}")
                    tt.addLine("Slice Y: ${sliceY.intValue + model.bounds.min.y}")
                }
        )

        // --- Left material bar (simple list from model.bom for now) ---
        val materialStacks = model.bom
            .sortedByDescending { it.count }
            .mapNotNull { entry ->
                when (val req = entry.requirement) {
                    is ExactBlockStateRequirement -> {
                        val block = net.minecraft.block.Block.REGISTRY.getObject(req.blockId) ?: return@mapNotNull null
                        net.minecraft.item.ItemStack(block, 1, req.meta)
                    }

                    else -> null
                }
            }

        val selectedStack = materialStacks.firstOrNull() ?: net.minecraft.item.ItemStack.EMPTY
        val selectedDrawable = com.cleanroommc.modularui.drawable.ItemDrawable(selectedStack)
        root.child(selectedDrawable.asWidget().pos(8, 31).size(16, 16))

        val materialsColumn = Column()
            .pos(8, 49)
            .size(16, 162)
            .childPadding(2)
            .apply { flex().coverChildrenWidth(); flex().coverChildrenHeight() }
        for (s in materialStacks.take(9)) {
            materialsColumn.child(com.cleanroommc.modularui.drawable.ItemDrawable(s).asWidget().size(16, 16))
        }
        root.child(materialsColumn)

        // --- 2D slice slider ---
        val handleNormal = subTex(230, 59, 7, 38)
        val handleHover = subTex(230, 72, 7, 38)
        val handlePressed = subTex(230, 85, 7, 38)
        root.child(
            github.kasuminova.prototypemachinery.client.preview.ui.widget.SliceSliderWidget(
                value = sliceY,
                maxProvider = { (model.bounds.max.y - model.bounds.min.y).coerceAtLeast(0) },
                handleNormal = handleNormal,
                handleHover = handleHover,
                handlePressed = handlePressed
            )
                .pos(161, 32)
                .size(10, 158)
        )

        // --- Bottom fold menu ---
        val menuExpanded = BoolValue(true)

        val menuToggle = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = subTex(185, 238, 14, 12),
            hover = subTex(199, 238, 14, 12),
            pressedOrEnabled = subTex(213, 238, 14, 12),
            onClick = { menuExpanded.boolValue = !menuExpanded.boolValue }
        )
            .pos(172, 207)
            .size(14, 12)
        root.child(menuToggle)

        // Buttons shown only when expanded (inside the bottom menu container).
        fun bottomButton(
            yTex: Int,
            tooltip: String,
            toggle: BoolValue? = null,
            onClick: (() -> Unit)? = null
        ): github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton {
            return github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
                normal = subTex(185, yTex, 12, 14),
                hover = subTex(198, yTex, 12, 14),
                pressedOrEnabled = subTex(211, yTex, 12, 14),
                toggle = toggle,
                onClick = onClick
            )
                .size(12, 14)
        }

        val bottomMenu = Flow.row()
            .pos(190, 206)
            .size(66, 14)
            .childPadding(2)
            .apply { flex().coverChildrenHeight() }
        root.child(bottomMenu)

        // 投影按钮（行为后续再对齐；目前保留为占位）
        val btnProjection = bottomButton(
            yTex = 16,
            tooltip = "Projection (todo)"
        ) {
            // TODO: projection mode (if/when we add it)
        }

        // 空按钮（占位）
        val btnEmpty = bottomButton(
            yTex = 31,
            tooltip = "(unused)"
        ) {
            // placeholder
        }

        // 方块轮换（目前暂时绑定为自动旋转开关；后续可替换为 materials cycle）
        val btnCycle = bottomButton(
            yTex = 46,
            tooltip = "Cycle blocks (todo)",
            toggle = autoRotate
        )

        // 切换 3D / 2D 预览
        val btnToggle3D2D = bottomButton(
            yTex = 61,
            tooltip = "Toggle 3D / 2D preview"
        ) {
            state.show3d.boolValue = !state.show3d.boolValue
        }

        bottomMenu.child(btnProjection)
        bottomMenu.child(btnEmpty)
        bottomMenu.child(btnCycle)
        bottomMenu.child(btnToggle3D2D)

        // Enable/disable the folded menu children based on [menuExpanded].
        root.onUpdateListener {
            val expanded = menuExpanded.boolValue
            if (bottomMenu.isEnabled != expanded) bottomMenu.isEnabled = expanded

            if (!host.allowWorldScan) return@onUpdateListener
            if (!state.enableScan.boolValue) return@onUpdateListener

            val w = mc.world ?: return@onUpdateListener
            val anchor = host.anchorProvider(mc) ?: return@onUpdateListener

            val sc = StructurePreviewWorldScanCache.getOrCreate(
                world = w,
                structureId = structureId,
                model = model,
                anchor = anchor
            )
            sc.tick(512)
            statusSnapshot = sc.snapshotStatuses()
        }

        panel.child(root)
        return panel
    }

    private fun formatBomLine(line: StructurePreviewBomLine): String {
        val req = formatRequirementShort(line.requirement)
        val tail = buildString {
            if (line.mismatchCount > 0) append("  mismatch=${line.mismatchCount}")
            if (line.unloaded > 0) append("  unloaded=${line.unloaded}")
            if (line.unknown > 0) append("  unknown=${line.unknown}")
        }
        return "${line.requiredCount}x $req$tail"
    }

    private fun colorForLine(line: StructurePreviewBomLine): Int {
        return when {
            line.mismatchCount > 0 -> Color.RED.main
            line.unloaded > 0 -> Color.ORANGE.main
            line.unknown > 0 -> Color.GREY.brighter(1)
            else -> Color.GREEN.main
        }
    }

    private fun formatRequirementShort(req: BlockRequirement): String {
        return when (req) {
            is ExactBlockStateRequirement -> {
                val base = req.blockId.toString()
                if (req.meta != 0) "$base@${req.meta}" else base
            }

            is LiteralRequirement -> req.key
            is UnknownRequirement -> "<unknown:${req.debug}>"
            else -> req.stableKey()
        }
    }
}
