package github.kasuminova.prototypemachinery.client.preview.ui

import com.cleanroommc.modularui.api.IThemeApi
import com.cleanroommc.modularui.api.drawable.IDrawable
import com.cleanroommc.modularui.api.widget.IWidget
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
import com.cleanroommc.modularui.widgets.ItemDisplayWidget
import com.cleanroommc.modularui.widgets.ListWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.TransformWidget
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.structure.preview.AnyOfRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.LiteralRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.UnknownRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewBomLine
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewStats
import github.kasuminova.prototypemachinery.client.preview.ProjectionRenderMode
import github.kasuminova.prototypemachinery.client.preview.ProjectionVisualMode
import github.kasuminova.prototypemachinery.client.preview.StructureProjectionSession
import github.kasuminova.prototypemachinery.client.preview.WorldProjectionManager
import github.kasuminova.prototypemachinery.client.preview.scan.StructurePreviewWorldScanCache
import github.kasuminova.prototypemachinery.client.preview.ui.widget.ScissorGroupWidget
import github.kasuminova.prototypemachinery.client.preview.ui.widget.StructurePreview3DWidget
import github.kasuminova.prototypemachinery.client.util.ClientNextTick
import github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.I18n
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraft.util.text.TextComponentTranslation
import net.minecraftforge.fluids.FluidRegistry
import net.minecraftforge.fluids.FluidStack
import net.minecraftforge.fluids.FluidUtil

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

    private fun tr(key: String, vararg args: Any): String = I18n.format(key, *args)

    private fun onOffText(v: Boolean): String = tr(if (v) "pm.preview.ui.state.on" else "pm.preview.ui.state.off")

    private fun previewModeText(show3d: Boolean): String = tr(if (show3d) "pm.preview.ui.mode.3d" else "pm.preview.ui.mode.slice")

    private fun entryStatusText(st: StructurePreviewEntryStatus): String = tr("pm.preview.ui.entry_status.${st.name.lowercase()}")

    /**
     * Best-effort resolve controller block requirement for a structure.
     *
     * Notes:
     * - UI currently opens by structureId only (no machineTypeId), so we infer via
     *   `MachineType.structure.id == structureId`.
     * - If multiple machine types share a structure, we pick a deterministic one (min id).
     */
    private fun resolveControllerRequirement(structureId: String): ExactBlockStateRequirement? {
        val candidates = PrototypeMachineryAPI.machineTypeRegistry.all()
            .filter { it.structure.id == structureId }
            .sortedBy { it.id.toString() }

        val machineType = candidates.firstOrNull() ?: return null

        // Controller block registry name convention: <namespace>:<path>_controller
        val controllerId = ResourceLocation(machineType.id.namespace, machineType.id.path + "_controller")
        val block = Block.REGISTRY.getObject(controllerId)
        if (block === Blocks.AIR) return null

        @Suppress("DEPRECATION")
        val state = block.defaultState

        val id = block.registryName ?: controllerId
        @Suppress("DEPRECATION")
        val meta = block.getMetaFromState(state)
        val props = state.propertyKeys.associate { prop ->
            val v = state.getValue(prop)
            prop.name to v.toString()
        }
        return ExactBlockStateRequirement(id, meta, props)
    }

    private fun guiTex(path: String): UITexture {
        // ResourceLocation path is without the textures/ prefix and without .png
        // e.g. textures/gui/gui_structure_preview/base.png -> gui/gui_structure_preview/base
        return UITexture.fullImage(ResourceLocation("prototypemachinery", "gui/gui_structure_preview/$path"))
    }

    private val PANEL_BG = guiTex("base")

    // Keep slot visuals consistent with ItemHatchGUI.
    // states.png sub-area: (73,0) size 18x18.
    private val ITEM_SLOT_BG: IDrawable = UITexture.builder()
        .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
        .imageSize(256, 256)
        .subAreaXYWH(73, 0, 18, 18)
        .build()

    private class ToggleDrawableWidget(
        private val valueProvider: () -> Boolean,
        private val off: com.cleanroommc.modularui.api.drawable.IDrawable,
        private val on: com.cleanroommc.modularui.api.drawable.IDrawable
    ) : com.cleanroommc.modularui.widget.Widget<ToggleDrawableWidget>() {
        override fun draw(context: com.cleanroommc.modularui.screen.viewport.ModularGuiContext, widgetTheme: com.cleanroommc.modularui.theme.WidgetThemeEntry<*>) {
            val drawable = if (valueProvider()) on else off
            drawable.drawAtZero(context, area, getActiveWidgetTheme(widgetTheme, isHovering))
            super.draw(context, widgetTheme)
        }
    }

    /**
     * A Column that we frequently rebuild (remove + re-add children) at runtime.
     *
     * ModularUI 的布局系统会在 children 变化时自行重新计算，所以这里只需要确保 removeAll() 被调用即可。
     */
    private class ClearableColumn : Column() {
        fun clearChildrenSafe() {
            removeAll()
            scheduleResize()
        }
    }

    /**
     * Transparent click-catcher that can wrap a child widget.
     * Used to make display-only widgets (like ItemDisplayWidget) clickable without changing their rendering.
     */
    private class ClickableOverlay(
        private val onLeftClick: () -> Unit
    ) : com.cleanroommc.modularui.widget.SingleChildWidget<ClickableOverlay>(), com.cleanroommc.modularui.api.widget.Interactable {
        override fun onMousePressed(mouseButton: Int): com.cleanroommc.modularui.api.widget.Interactable.Result {
            if (mouseButton != 0) return com.cleanroommc.modularui.api.widget.Interactable.Result.ACCEPT
            onLeftClick.invoke()
            return com.cleanroommc.modularui.api.widget.Interactable.Result.SUCCESS
        }
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

    private class RootRuntime(
        var statusSnapshot: Map<BlockPos, StructurePreviewEntryStatus> = emptyMap(),
        val issuesOnly: BoolValue = BoolValue(false),
        val autoRotate: BoolValue = BoolValue(false),
        val showWireframe: BoolValue = BoolValue(true),
        val sliceY: IntValue,
    ) {
        var clickedPos: BlockPos? = null
        var clickedRequirement: BlockRequirement? = null

        // 0 = expanded, 1 = collapsed
        // NOTE: This is updated per-frame (dt-based) to avoid visible 20tps stepping.
        var collapseNow: Float = 0.0f
        var sidebarNow: Float = 1.0f // 0 = shown, 1 = hidden
        var animLastFrameMs: Long = -1L

        var rebuildMaterialsUi: (() -> Unit)? = null
        var rebuildReplaceUi: (() -> Unit)? = null
    }

    private data class MaterialsUiParts(
        val materialPinnedOpen: BoolValue,
        val materialPreviewUiBg: com.cleanroommc.modularui.widget.Widget<*>,
        val materialPreviewUiContent: ClearableColumn
    )

    /**
     * Kotlin helper to avoid dealing with ModularUI's self-referential generics
     * (ListWidget<I, W extends ListWidget<I, W>>) at call sites.
     */
    private class WidgetList : ListWidget<IWidget, WidgetList>()

    fun open(
        structureId: String,
        sliceCountOverride: Int?,
        host: StructurePreviewUiHostConfig = StructurePreviewUiHostConfig.standalone(),
        showMaterials: Boolean = true,
        selectedPositionsProvider: (() -> Set<BlockPos>?)? = null
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
            structureName = structure.name,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider
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

    /**
     * Build a read-only structure preview panel for embedding into another host (e.g. JEI).
     *
     * This does NOT open a screen; it only returns the ModularUI panel.
     */
    fun createPanel(
        structureId: String,
        sliceCountOverride: Int?,
        host: StructurePreviewUiHostConfig,
        showMaterials: Boolean = true,
        selectedPositionsProvider: (() -> Set<BlockPos>?)? = null,
    ): ModularPanel? {
        val structure = PrototypeMachineryAPI.structureRegistry.get(structureId) ?: return null

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
        return buildPanel(
            state = state,
            model = model,
            structureId = structureId,
            structureName = structure.name,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider
        )
    }

    /**
     * Build the preview UI root widget for embedding into another screen/panel.
     *
     * 注意：返回的是 root widget（184x220 的绝对布局 Column），并不会创建/返回 ModularPanel。
     */
    fun createEmbeddedRoot(
        structureId: String,
        sliceCountOverride: Int?,
        host: StructurePreviewUiHostConfig,
        showMaterials: Boolean = true,
        selectedPositionsProvider: (() -> Set<BlockPos>?)? = null,
    ): IWidget? {
        val structure = PrototypeMachineryAPI.structureRegistry.get(structureId) ?: return null

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
        return buildRoot(
            state = state,
            model = model,
            structureId = structureId,
            structureName = structure.name,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider
        )
    }

    private fun buildPanel(
        state: StructurePreviewUiState,
        model: StructurePreviewModel,
        structureId: String,
        structureName: String,
        host: StructurePreviewUiHostConfig,
        showMaterials: Boolean,
        selectedPositionsProvider: (() -> Set<BlockPos>?)?
    ): ModularPanel {
        val panel = ModularPanel.defaultPanel("structure_preview_ui")
            .size(184, 220)
            .background(PANEL_BG)

        if (host.showCloseButton) {
            panel.child(ButtonWidget.panelCloseButton())
        }

        val root = buildRoot(
            state = state,
            model = model,
            structureId = structureId,
            structureName = structureName,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider
        )

        panel.child(root)
        return panel
    }

    private fun buildRoot(
        state: StructurePreviewUiState,
        model: StructurePreviewModel,
        structureId: String,
        structureName: String,
        host: StructurePreviewUiHostConfig,
        showMaterials: Boolean,
        selectedPositionsProvider: (() -> Set<BlockPos>?)?
    ): Column {
        // Root container (fixed-position children).
        // IMPORTANT: avoid fluent chaining here; some ModularUI Java APIs return Flow (platform type)
        // and Kotlin may widen the inferred type from Column to Flow, breaking our declared return type.
        val root = Column()
        root.pos(0, 0)
        root.size(184, 220)

        val mc = Minecraft.getMinecraft()

        // Hosts that hide scan controls may still want scan enabled by default.
        if (host.allowWorldScan && host.defaultEnableScan) {
            state.enableScan.boolValue = true
        }

        val controllerReq: ExactBlockStateRequirement? = resolveControllerRequirement(structureId)

        // --- Bottom layer: render panel (can be covered by all other elements) ---
        // Keep slider range consistent with 3D widget: include origin (0,0,0) in bounds.
        val minY = kotlin.math.min(model.bounds.min.y, 0)
        val maxY = kotlin.math.max(model.bounds.max.y, 0)
        val sizeY = (maxY - minY + 1).coerceAtLeast(1)
        val sliceY = IntValue((sizeY / 2).coerceIn(0, sizeY - 1))
        val rt = RootRuntime(sliceY = sliceY)

        // Host-selected positions + local click selection.
        val combinedSelectedPositionsProvider: (() -> Set<BlockPos>?)? = {
            val base = selectedPositionsProvider?.invoke()
            val local = rt.clickedPos
            if (base == null && local == null) {
                null
            } else {
                val out = LinkedHashSet<BlockPos>()
                if (base != null) out.addAll(base)
                if (local != null) out.add(local)
                out
            }
        }

        val view3d = StructurePreview3DWidget(
            model = model,
            controllerRequirement = controllerReq,
            statusProvider = { rt.statusSnapshot },
            issuesOnlyProvider = { rt.issuesOnly.boolValue },
            sliceModeProvider = { !state.show3d.boolValue },
            sliceYProvider = { rt.sliceY.intValue },
            autoRotateProvider = { rt.autoRotate.boolValue },
            wireframeProvider = { rt.showWireframe.boolValue },
            selectedPositionsProvider = combinedSelectedPositionsProvider,
            onBlockClicked = { pos, req ->
                // Toggle selection on repeated click.
                val lastPos = rt.clickedPos
                if (lastPos != null && lastPos == pos) {
                    rt.clickedPos = null
                    rt.clickedRequirement = null
                    state.selectedKey = null
                } else {
                    rt.clickedPos = pos
                    rt.clickedRequirement = req
                    state.selectedKey = req.stableKey()
                }
            }
        )
            .pos(6, 13)
            // Keep interactive view away from the right slider + bottom buttons.
            .size(171, 200)

        root.child(view3d)

        // --- Bottom (clipped): down_button group ---
        // Declared early because top/bottom animations are driven by the same collapse value.
        val bottomCollapsed = BoolValue(false)

        // Update collapseNow early every frame so all TransformWidgets see a fresh value.
        class CollapseAnimatorWidget : com.cleanroommc.modularui.widget.Widget<CollapseAnimatorWidget>() {
            override fun draw(context: com.cleanroommc.modularui.screen.viewport.ModularGuiContext, widgetTheme: com.cleanroommc.modularui.theme.WidgetThemeEntry<*>) {
                val nowMs = Minecraft.getSystemTime()
                val dtSec = if (rt.animLastFrameMs < 0L) 0.0 else ((nowMs - rt.animLastFrameMs).toDouble() / 1000.0)
                rt.animLastFrameMs = nowMs

                // Clamp large dt to avoid snapping after alt-tab / pauses.
                val clampedDt = kotlin.math.min(dtSec, 0.25)
                val target = if (bottomCollapsed.boolValue) 1.0f else 0.0f

                // Exponential smoothing: alpha = 1 - exp(-dt / tau)
                // Smaller tau => faster response.
                val tau = 0.10
                val alpha = (1.0 - kotlin.math.exp(-clampedDt / tau)).toFloat()

                rt.collapseNow += (target - rt.collapseNow) * alpha
                if (kotlin.math.abs(target - rt.collapseNow) < 0.0005f) rt.collapseNow = target

                // Left side bar auto-show/hide uses the same smoothing to match component_switch.
                // When no block is selected: hide (1). When selected: show (0).
                val sidebarTarget = if (rt.clickedRequirement == null) 1.0f else 0.0f
                rt.sidebarNow += (sidebarTarget - rt.sidebarNow) * alpha
                if (kotlin.math.abs(sidebarTarget - rt.sidebarNow) < 0.0005f) rt.sidebarNow = sidebarTarget
                super.draw(context, widgetTheme)
            }
        }
        root.child(CollapseAnimatorWidget())

        // --- Top (clipped): components slide up and get masked within their own rectangles ---
        // NOTE: In the spec, each top component slides up when component_switch is pressed,
        // and the part outside the *component's own* area should be masked.

        // machine_prefix_name @ (8,11) size 115x18, slide Y -16
        val titleClip = ScissorGroupWidget()
            .pos(7, 10)
            .size(115, 18)
        val titleContent = Column()
            .pos(0, 0)
            .size(115, 18)

        val topTitleBg = guiTex("top_machine_prefix_name_base").asWidget().pos(0, 0).size(115, 18)
        val title = if (structureName.isNotBlank()) structureName else structureId
        val topTitleText = TextWidget(title)
            .pos(3, 6)
            .color(Color.WHITE.main)
            .shadow(false)
            .maxWidth(153)
        titleContent.child(topTitleBg)
        titleContent.child(topTitleText)

        val titleSlide = TransformWidget(titleContent)
            .pos(0, 0)
            .size(115, 18)
            .transform { stack ->
                val t = rt.collapseNow.coerceIn(0.0f, 1.0f)
                stack.translate(0.0f, -16.0f * t)
            }
        titleClip.child(titleSlide)
        root.child(titleClip)

        // --- Top: buttons ---
        // Preview reset
        // preview_reset @ (124,11) base 17x17, trigger height 19, slide Y -15
        val previewResetClip = ScissorGroupWidget()
            .pos(123, 10)
            .size(17, 19)
        val previewResetContent = Column()
            .pos(0, 0)
            .size(17, 19)

        val previewResetBase = guiTex("top_button_base_preview_reset").asWidget().pos(0, 0).size(17, 17)
        val previewResetBtn = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("top_button/self_resetting_default"),
            hover = guiTex("top_button/self_resetting_selected"),
            pressed = guiTex("top_button/self_resetting_press"),
            onClick = { view3d.resetView() }
        )
            .pos(3, 15)
            .size(13, 5)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.reset_view"))
            }
        previewResetContent.child(previewResetBase)
        previewResetContent.child(previewResetBtn)

        val previewResetSlide = TransformWidget(previewResetContent)
            .pos(0, 0)
            .size(17, 19)
            .transform { stack ->
                val t = rt.collapseNow.coerceIn(0.0f, 1.0f)
                stack.translate(0.0f, -15.0f * t)
            }
        previewResetClip.child(previewResetSlide)
        root.child(previewResetClip)

        // Structural forming (toggle) - base switches off/on
        val structuralForming = BoolValue(false)
        // structural_forming @ (142,11) base 17x17, trigger height 19, slide Y -15
        val structuralFormingClip = ScissorGroupWidget()
            .pos(141, 10)
            .size(17, 19)
        val structuralFormingContent = Column()
            .pos(0, 0)
            .size(17, 19)

        val structuralFormingBase = ToggleDrawableWidget(
            valueProvider = { structuralForming.boolValue },
            off = guiTex("top_button_base_structural_forming_off"),
            on = guiTex("top_button_base_structural_forming_on")
        )
            .pos(0, 0)
            .size(17, 17)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.structural_forming"))
            }
        val structuralFormingBtn = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("top_button/self_locking_default"),
            hover = guiTex("top_button/self_locking_selected"),
            pressed = guiTex("top_button/self_locking_pressed"),
            enabled = guiTex("top_button/self_locking_selected"),
            toggle = structuralForming
        )
            .pos(3, 15)
            .size(13, 5)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.structural_forming"))
            }

        structuralFormingContent.child(structuralFormingBase)
        structuralFormingContent.child(structuralFormingBtn)

        val structuralFormingSlide = TransformWidget(structuralFormingContent)
            .pos(0, 0)
            .size(17, 19)
            .transform { stack ->
                val t = rt.collapseNow.coerceIn(0.0f, 1.0f)
                stack.translate(0.0f, -15.0f * t)
            }
        structuralFormingClip.child(structuralFormingSlide)
        root.child(structuralFormingClip)

        // Structural information (hover tooltip)
        // structural_information @ (160,11) size 17x18, slide Y -15
        val structuralInfoClip = ScissorGroupWidget()
            .pos(159, 10)
            .size(17, 18)
        val structuralInfoContent = Column()
            .pos(0, 0)
            .size(17, 18)

        val structuralInfo = UiButton()
            .pos(0, 0)
            .size(17, 18)
            .background(guiTex("top_button_base_structural_information"))
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.struct_info.structure", structureId))
                tt.addLine(tr("pm.preview.ui.struct_info.scan", onOffText(state.enableScan.boolValue)))
                tt.addLine(tr("pm.preview.ui.struct_info.mode", previewModeText(state.show3d.boolValue)))
                tt.addLine(tr("pm.preview.ui.struct_info.slice_y", (sliceY.intValue + model.bounds.min.y)))
                tt.addLine(tr("pm.preview.ui.struct_info.forming", onOffText(structuralForming.boolValue)))
            }
        structuralInfoContent.child(structuralInfo)

        val structuralInfoSlide = TransformWidget(structuralInfoContent)
            .pos(0, 0)
            .size(17, 18)
            .transform { stack ->
                val t = rt.collapseNow.coerceIn(0.0f, 1.0f)
                stack.translate(0.0f, -15.0f * t)
            }
        structuralInfoClip.child(structuralInfoSlide)
        root.child(structuralInfoClip)

        // --- Optional: materials UI ---
        val materials: MaterialsUiParts? = if (showMaterials) {
            buildMaterialsSection(
                root = root,
                host = host,
                model = model,
                rt = rt
            )
        } else {
            null
        }

        // --- Right: layer selection slider ---
        val layerPreview = BoolValue(false)
        val layerSelectionBg = guiTex("right_layer_selection").asWidget().pos(165, 33).size(10, 158)
        root.child(layerSelectionBg)

        val sliderNormal = guiTex("slider_default")
        val sliderHover = guiTex("slider_selected")
        val sliderPressed = guiTex("slider_press")
        val layerSlider = github.kasuminova.prototypemachinery.client.preview.ui.widget.SliceSliderWidget(
            value = sliceY,
            maxProvider = { (model.bounds.max.y - model.bounds.min.y).coerceAtLeast(0) },
            handleNormal = sliderNormal,
            handleHover = sliderHover,
            handlePressed = sliderPressed,
            handleW = 7,
            handleH = 12,
            trackPaddingX = 3,
            trackPaddingY = 11
        )
            .pos(165, 33)
            .size(10, 158)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.slice_y", (sliceY.intValue + model.bounds.min.y)))
            }
        root.child(layerSlider)

        // --- Bottom (clipped): down_button group ---
        // Spec: down_button component is 78x16 at (100,196). When collapsed, inner button group shifts +57px and is clipped.
        val bottomClip = ScissorGroupWidget()
            .pos(98, 195)
            .size(78, 16)

        // Slide content container (everything except component_switch)
        val bottomSlideContent = Column()
            .pos(0, 0)
            .size(78, 16)

        // Base background + LED are stateful (positions are local to bottomSlideContent)
        val baseBg = ToggleDrawableWidget(
            valueProvider = { !bottomCollapsed.boolValue },
            off = guiTex("down_button_base_on"), // off?
            on = guiTex("down_button_base_on")
        )
            // Texture is 62x13; place it at y=3 so it fits the 78x16 clip region like the spec.
            .pos(0, 3)
            .size(62, 13)
        bottomSlideContent.child(baseBg)

        val led = ToggleDrawableWidget(
            valueProvider = { bottomCollapsed.boolValue },
            off = guiTex("down_button_base_off-led"),
            on = guiTex("down_button_base_on-led")
        )
            // LED texture is 1x7; align relative to baseBg (x=2, y=baseY+3).
            .pos(2, 6)
            .size(1, 7)
        bottomSlideContent.child(led)

        val bottomSlide = TransformWidget(bottomSlideContent)
            .pos(0, 0)
            .size(78, 16)
            .transform { stack ->
                val t = rt.collapseNow.coerceIn(0.0f, 1.0f)
                stack.translate(57.0f * t, 0.0f)
            }

        bottomClip.child(bottomSlide)

        // component_switch (toggle) - stays fixed; does NOT slide with the collapsed group
        val componentSwitch = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/component_switch_default"),
            hover = guiTex("down_button_selected/component_switch_selected"),
            pressed = guiTex("down_button_pressed/component_switch_pressed"),
            enabled = guiTex("down_button_selected/component_switch_selected"),
            toggle = bottomCollapsed
        )
            .pos(67, 2)
            .size(12, 14)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.component_switch"))
            }
        bottomClip.child(componentSwitch)

        // place_projection (momentary)
        val placeProjection = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/place_projection_default"),
            hover = guiTex("down_button_selected/place_projection_selected"),
            pressed = guiTex("down_button_pressed/place_projection_pressed"),
            onClick = onClick@{
                val anchor = host.anchorProvider(mc) ?: mc.player?.position
                val a = anchor ?: return@onClick
                WorldProjectionManager.start(
                    StructureProjectionSession(
                        structureId = structureId,
                        anchor = a,
                        sliceCountOverride = null,
                        followPlayerFacing = true,
                        maxRenderDistance = null,
                        renderMode = ProjectionRenderMode.ALL,
                        visualMode = ProjectionVisualMode.GHOST
                    )
                )
            }
        )
            .pos(5, 0)
            .size(12, 14)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.place_projection"))
            }

        // material preview ui (toggle)
        val materialPreviewBtn = if (showMaterials) github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/material_preview_ui_default"),
            hover = guiTex("down_button_selected/material_preview_ui_selected"),
            pressed = guiTex("down_button_pressed/material_preview_ui_pressed"),
            enabled = guiTex("down_button_selected/material_preview_ui_selected"),
            toggle = materials!!.materialPinnedOpen
        )
            .pos(18, 0)
            .size(12, 14)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.material_panel"))
            } else null

        // replace block cycle lock (toggle) - repurposed: toggle wireframe overlay (so block model rendering is visible)
        val replaceBlock = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/replace_block_default"),
            hover = guiTex("down_button_selected/replace_block_selected"),
            pressed = guiTex("down_button_pressed/replace_block_pressed"),
            enabled = guiTex("down_button_selected/replace_block_selected"),
            toggle = rt.showWireframe
        )
            .pos(31, 0)
            .size(12, 14)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.wireframe"))
            }

        // layer preview (toggle)
        val layerPreviewBtn = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/layer_preview_default"),
            hover = guiTex("down_button_selected/layer_preview_selected"),
            pressed = guiTex("down_button_pressed/layer_preview_pressed"),
            enabled = guiTex("down_button_selected/layer_preview_selected"),
            toggle = layerPreview
        )
            .pos(44, 0)
            .size(12, 14)
            .tooltipDynamic { tt ->
                tt.addLine(tr("pm.preview.ui.tooltip.layer_preview"))
            }

        bottomSlideContent.child(placeProjection)
        if (showMaterials) {
            bottomSlideContent.child(materialPreviewBtn!!)
        }
        bottomSlideContent.child(replaceBlock)
        bottomSlideContent.child(layerPreviewBtn)

        root.child(bottomClip)

        // Enable/disable the folded menu children based on [menuExpanded].
        root.onUpdateListener {
            // bottom collapsed => disable base buttons
            val collapsed = bottomCollapsed.boolValue
            if (placeProjection.isEnabled == collapsed) placeProjection.isEnabled = !collapsed
            if (showMaterials && materialPreviewBtn!!.isEnabled == collapsed) materialPreviewBtn.isEnabled = !collapsed
            if (replaceBlock.isEnabled == collapsed) replaceBlock.isEnabled = !collapsed
            if (layerPreviewBtn.isEnabled == collapsed) layerPreviewBtn.isEnabled = !collapsed

            // Tie preview mode to the layer preview toggle.
            state.show3d.boolValue = !layerPreview.boolValue

            if (showMaterials) {
                // material preview UI visibility:
                // - pinned open: show
                // - otherwise: keep hidden (auto-pop is handled by the left side bar now)
                val showMat = materials!!.materialPinnedOpen.boolValue
                if (materials.materialPreviewUiBg.isEnabled != showMat) materials.materialPreviewUiBg.isEnabled = showMat
                if (materials.materialPreviewUiContent.isEnabled != showMat) materials.materialPreviewUiContent.isEnabled = showMat
            }

            // layer selection visibility
            val showLayer = layerPreview.boolValue
            if (layerSelectionBg.isEnabled != showLayer) layerSelectionBg.isEnabled = showLayer
            if (layerSlider.isEnabled != showLayer) layerSlider.isEnabled = showLayer

            // When not in layer preview, keep the slider panel tucked to the right (+8px) like the spec.
            val layerX = if (showLayer) 165 else 173
            layerSelectionBg.pos(layerX, 33)
            layerSlider.pos(layerX, 33)

            if (host.allowWorldScan && state.enableScan.boolValue) {
                val w = mc.world
                val anchor = host.anchorProvider(mc)
                if (w != null && anchor != null) {
                    val sc = StructurePreviewWorldScanCache.getOrCreate(
                        world = w,
                        structureId = structureId,
                        model = model,
                        anchor = anchor
                    )
                    sc.tick(512)
                    rt.statusSnapshot = sc.snapshotStatuses()
                }
            }

            rt.rebuildMaterialsUi?.invoke()
            rt.rebuildReplaceUi?.invoke()
        }

        return root
    }

    private fun buildMaterialsSection(
        root: Column,
        host: StructurePreviewUiHostConfig,
        model: StructurePreviewModel,
        rt: RootRuntime
    ): MaterialsUiParts {
        // 默认不打开材料界面。
        // When not pinned, materials UI is auto-shown only when a block is selected.
        val materialPinnedOpen = BoolValue(false)
        val materialPreviewUiBg = guiTex("base_material_preview_ui").asWidget().pos(29, 31).size(134, 163)
        val materialPreviewUiContent: ClearableColumn = run {
            val c = ClearableColumn()
            c.pos(33, 35)
            c.size(111, 160)
            c.childPadding(2)
            c.flex().coverChildrenWidth()
            c.flex().coverChildrenHeight()
            c
        }

        fun makeSlot(
            stack: ItemStack,
            displayAmount: Boolean,
            tooltipLines: () -> List<String>,
            onLeftClick: (() -> Unit)? = null
        ): com.cleanroommc.modularui.widget.Widget<*> {
            val slot = ItemDisplayWidget()
                .item(stack)
                .displayAmount(displayAmount)
                .size(18)
                // Avoid theme-provided slot background; we draw our own.
                .widgetTheme(IThemeApi.FALLBACK)
                .background(ITEM_SLOT_BG)
                .disableHoverBackground()

            val attachTooltip: (com.cleanroommc.modularui.widget.Widget<*>) -> Unit = { ttWidget ->
                ttWidget.tooltipDynamic { tt ->
                    for (line in tooltipLines()) {
                        tt.addLine(line)
                    }
                }
                ttWidget.tooltipAutoUpdate(true)
            }

            return if (onLeftClick != null) {
                val overlay = ClickableOverlay(onLeftClick)
                    .size(18)
                    .child(slot)
                attachTooltip(overlay)
                overlay
            } else {
                attachTooltip(slot)
                slot
            }
        }

        fun stateFromReq(req: ExactBlockStateRequirement): net.minecraft.block.state.IBlockState? {
            val block = Block.REGISTRY.getObject(req.blockId) ?: return null
            if (block == Blocks.AIR) return null
            return try {
                @Suppress("DEPRECATION")
                block.getStateFromMeta(req.meta)
            } catch (_: Throwable) {
                null
            }
        }

        fun stackForExact(req: ExactBlockStateRequirement, count: Int): ItemStack? {
            val block = Block.REGISTRY.getObject(req.blockId) ?: return null
            if (block == Blocks.AIR) return null

            val st = ItemStack(block, count.coerceAtLeast(1), req.meta)
            if (!st.isEmpty) return st

            // Fluid blocks: use a filled bucket for display.
            val state = stateFromReq(req)
            val fluid = FluidRegistry.lookupFluidForBlock(block)
                ?: when (state?.material) {
                    net.minecraft.block.material.Material.WATER -> FluidRegistry.WATER
                    net.minecraft.block.material.Material.LAVA -> FluidRegistry.LAVA
                    else -> null
                }
            if (fluid != null) {
                val bucket = FluidUtil.getFilledBucket(FluidStack(fluid, 1000))
                if (!bucket.isEmpty) {
                    bucket.count = count.coerceAtLeast(1)
                    return bucket
                }
            }
            return null
        }

        fun resolveAnyOfOption(anyOf: AnyOfRequirement): ExactBlockStateRequirement {
            val reqKey = anyOf.stableKey()
            val chosenKey = host.anyOfSelectionProvider(reqKey)
            return anyOf.options.firstOrNull { it.stableKey() == chosenKey } ?: anyOf.options.first()
        }

        fun displayStackForRequirement(req: BlockRequirement, count: Int): ItemStack? {
            return when (req) {
                is ExactBlockStateRequirement -> stackForExact(req, count)
                is AnyOfRequirement -> stackForExact(resolveAnyOfOption(req), count)
                else -> null
            }
        }

        fun computeDisplayBom(): List<Pair<BlockRequirement, Int>> {
            // When a block is selected, show only that position's requirement.
            val selected = rt.clickedRequirement
            if (selected != null) {
                return listOf(selected to 1)
            }

            if (host.materialsMode == StructurePreviewUiHostConfig.MaterialsMode.REMAINING && rt.statusSnapshot.isNotEmpty()) {
                data class Acc(var req: BlockRequirement, var count: Int)
                val byKey = LinkedHashMap<String, Acc>()
                for ((relPos, req) in model.blocks) {
                    val st = rt.statusSnapshot[relPos] ?: continue
                    if (st != StructurePreviewEntryStatus.MISSING && st != StructurePreviewEntryStatus.WRONG) continue
                    val key = req.stableKey()
                    val acc = byKey.getOrPut(key) { Acc(req, 0) }
                    acc.count++
                }
                return byKey.values
                    .filter { it.count > 0 }
                    .sortedByDescending { it.count }
                    .map { it.req to it.count }
            }

            return model.bom
                .asSequence()
                .map { it.requirement to it.count }
                .sortedByDescending { it.second }
                .toList()
        }

        // --- Left: replace preview (collapsible like top/bottom menus) ---
        // Spec note: treat this as a self-contained clipped component that slides out when component_switch is toggled.
        val leftReplaceClip = ScissorGroupWidget()
            .pos(9, 31)
            .size(18, 181)

        val leftReplaceContent: ClearableColumn = run {
            val c = ClearableColumn()
            c.pos(0, 0)
            c.size(18, 181)
            c
        }

        leftReplaceContent.child(guiTex("left_replace_preview").asWidget().pos(0, 0).size(18, 181))

        val leftReplaceSlide = TransformWidget(leftReplaceContent)
            .pos(0, 0)
            .size(18, 181)
            .transform { stack ->
                // Auto-hide side bar when no selection; show it when user clicked any block.
                // Also respects the component_switch collapse animation.
                val switchT = rt.collapseNow.coerceIn(0.0f, 1.0f)
                val selectionT = rt.sidebarNow.coerceIn(0.0f, 1.0f)
                val t = kotlin.math.max(switchT, selectionT)
                // Slide out to the left within the clip window.
                stack.translate(-18.0f * t, 0.0f)
            }

        leftReplaceClip.child(leftReplaceSlide)
        root.child(leftReplaceClip)

        // --- Material preview UI (dynamic; toggled by bottom button) ---
        root.child(materialPreviewUiBg)
        root.child(materialPreviewUiContent)

        var lastMatKey: String? = null
        var lastReplaceKey: String? = null

        rt.rebuildMaterialsUi = {
            run {
                val bom = computeDisplayBom()
                    .asSequence()
                    .mapNotNull { (req, cnt) ->
                        val stack = displayStackForRequirement(req, cnt) ?: return@mapNotNull null
                        Triple(req, stack, cnt)
                    }
                    .take(24)
                    .toList()

                val key = bom.joinToString("|") { (_, s, _) ->
                    val rn = s.item.registryName?.toString() ?: s.item.javaClass.name
                    "$rn:${s.metadata}:${s.count}"
                }
                if (key == lastMatKey) return@run
                lastMatKey = key

                materialPreviewUiContent.clearChildrenSafe()
                for ((req, s, cnt) in bom) {
                    val isRemaining = host.materialsMode == StructurePreviewUiHostConfig.MaterialsMode.REMAINING
                    val status = rt.clickedPos?.let { pos -> rt.statusSnapshot[pos] }
                    val lines = mutableListOf<String>()

                    // Item name
                    val name = if (s.isEmpty) tr("pm.preview.ui.empty") else s.displayName
                    lines.add(name)

                    // Requirement + count
                    if (rt.clickedRequirement != null) {
                        lines.add(tr("pm.preview.ui.requirement", formatRequirementShort(req)))
                        if (status != null) lines.add(tr("pm.preview.ui.status", entryStatusText(status)))
                        lines.add(tr("pm.preview.ui.count", cnt))
                    } else {
                        lines.add(tr(if (isRemaining) "pm.preview.ui.remaining" else "pm.preview.ui.required", cnt))
                        lines.add(tr("pm.preview.ui.key", req.stableKey()))
                    }

                    if (req is AnyOfRequirement) {
                        lines.add(tr("pm.preview.ui.replaceable_hint", req.options.size))
                    }

                    materialPreviewUiContent.child(
                        makeSlot(
                            stack = s,
                            displayAmount = true,
                            tooltipLines = { lines }
                        )
                    )
                }
            }
        }

        rt.rebuildReplaceUi = {
            run {
                val clicked = rt.clickedRequirement
                val anyOf = clicked as? AnyOfRequirement
                val key = clicked?.stableKey() ?: "<none>"
                if (key == lastReplaceKey) return@run
                lastReplaceKey = key

                leftReplaceContent.clearChildrenSafe()
                leftReplaceContent.child(guiTex("left_replace_preview").asWidget().pos(0, 0).size(18, 181))

                if (clicked == null) {
                    leftReplaceContent.child(
                        makeSlot(
                            stack = ItemStack.EMPTY,
                            displayAmount = false,
                            tooltipLines = { listOf(tr("pm.preview.ui.no_selection.title"), tr("pm.preview.ui.no_selection.hint")) }
                        ).pos(0, 0)
                    )
                    return@run
                }

                // Non-AnyOf requirement: show the required stack in the top slot only.
                if (anyOf == null) {
                    val stack = displayStackForRequirement(clicked, 1) ?: ItemStack.EMPTY
                    leftReplaceContent.child(
                        makeSlot(
                            stack = stack,
                            displayAmount = false,
                            tooltipLines = {
                                listOf(
                                    if (!stack.isEmpty) stack.displayName else tr("pm.preview.ui.empty"),
                                    tr("pm.preview.ui.requirement", formatRequirementShort(clicked)),
                                    tr("pm.preview.ui.key", clicked.stableKey())
                                )
                            }
                        ).pos(0, 0)
                    )
                    return@run
                }

                val reqKey = anyOf.stableKey()
                val selected = resolveAnyOfOption(anyOf)
                val selectedStack = stackForExact(selected, 1) ?: ItemStack.EMPTY

                leftReplaceContent.child(
                    makeSlot(
                        stack = selectedStack,
                        displayAmount = false,
                        tooltipLines = {
                            listOf(
                                tr("pm.preview.ui.current_selection", selectedStack.displayName),
                                tr("pm.preview.ui.anyof_choose_one", anyOf.options.size),
                                tr("pm.preview.ui.key", reqKey)
                            )
                        }
                    ).pos(0, 0)
                )

                val optionsColumn = ClearableColumn()
                    .pos(0, 18)
                    .size(18, 163)
                    .apply { flex().coverChildrenWidth(); flex().coverChildrenHeight() }

                var added = 0
                for (opt in anyOf.options) {
                    if (opt.stableKey() == selected.stableKey()) continue
                    val st = stackForExact(opt, 1) ?: continue

                    val optKey = opt.stableKey()
                    val onClick = host.anyOfSelectionSetter?.let { setter ->
                        { setter.invoke(reqKey, optKey) }
                    }

                    optionsColumn.child(
                        makeSlot(
                            stack = st,
                            displayAmount = false,
                            tooltipLines = {
                                listOf(
                                    tr("pm.preview.ui.candidate", st.displayName),
                                    if (onClick != null) tr("pm.preview.ui.click_to_select") else tr("pm.preview.ui.read_only"),
                                    tr("pm.preview.ui.key", optKey)
                                )
                            },
                            onLeftClick = onClick
                        )
                    )
                    added++
                    if (added >= 9) break
                }

                leftReplaceContent.child(optionsColumn)
            }
        }

        // Initial build.
        rt.rebuildMaterialsUi?.invoke()
        rt.rebuildReplaceUi?.invoke()

        return MaterialsUiParts(
            materialPinnedOpen = materialPinnedOpen,
            materialPreviewUiBg = materialPreviewUiBg,
            materialPreviewUiContent = materialPreviewUiContent
        )
    }

    private fun formatBomLine(line: StructurePreviewBomLine): String {
        val req = formatRequirementShort(line.requirement)
        val tail = buildString {
            if (line.mismatchCount > 0) append(tr("pm.preview.ui.bom.mismatch", line.mismatchCount))
            if (line.unloaded > 0) append(tr("pm.preview.ui.bom.unloaded", line.unloaded))
            if (line.unknown > 0) append(tr("pm.preview.ui.bom.unknown", line.unknown))
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
            is UnknownRequirement -> tr("pm.preview.ui.unknown_requirement", req.debug)
            else -> req.stableKey()
        }
    }
}
