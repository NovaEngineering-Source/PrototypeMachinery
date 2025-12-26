package github.kasuminova.prototypemachinery.client.preview.ui

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
import com.cleanroommc.modularui.widgets.ListWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.TransformWidget
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
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
import net.minecraft.init.Blocks
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

    /**
     * Build a read-only structure preview panel for embedding into another host (e.g. JEI).
     *
     * This does NOT open a screen; it only returns the ModularUI panel.
     */
    fun createPanel(
        structureId: String,
        sliceCountOverride: Int?,
        host: StructurePreviewUiHostConfig,
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
            host = host
        )
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

        if (host.showCloseButton) {
            panel.child(ButtonWidget.panelCloseButton())
        }

        // Root container (fixed-position children).
        val root = Column()
            .pos(0, 0)
            .size(184, 220)

        val mc = Minecraft.getMinecraft()

        val controllerReq: ExactBlockStateRequirement? = resolveControllerRequirement(structureId)

        // --- Bottom layer: render panel (can be covered by all other elements) ---
        var statusSnapshot: Map<BlockPos, StructurePreviewEntryStatus> = emptyMap()
        val issuesOnly = BoolValue(false)
        val autoRotate = BoolValue(false)
        val showWireframe = BoolValue(true)

        // Keep slider range consistent with 3D widget: include origin (0,0,0) in bounds.
        val minY = kotlin.math.min(model.bounds.min.y, 0)
        val maxY = kotlin.math.max(model.bounds.max.y, 0)
        val sizeY = (maxY - minY + 1).coerceAtLeast(1)
        val sliceY = IntValue((sizeY / 2).coerceIn(0, sizeY - 1))

        val view3d = StructurePreview3DWidget(
            model = model,
            controllerRequirement = controllerReq,
            statusProvider = { statusSnapshot },
            issuesOnlyProvider = { issuesOnly.boolValue },
            sliceModeProvider = { !state.show3d.boolValue },
            sliceYProvider = { sliceY.intValue },
            autoRotateProvider = { autoRotate.boolValue },
            wireframeProvider = { showWireframe.boolValue }
        )
            .pos(6, 13)
            // Keep interactive view away from the right slider + bottom buttons.
            .size(171, 200)

        root.child(view3d)

        // --- Bottom (clipped): down_button group ---
        // Declared early because top/bottom animations are driven by the same collapse value.
        val bottomCollapsed = BoolValue(false)

        // 0 = expanded, 1 = collapsed
        // NOTE: This is updated per-frame (dt-based) to avoid visible 20tps stepping.
        var collapseNow = 0.0f
        var collapseLastFrameMs = -1L

        // Update collapseNow early every frame so all TransformWidgets see a fresh value.
        class CollapseAnimatorWidget : com.cleanroommc.modularui.widget.Widget<CollapseAnimatorWidget>() {
            override fun draw(context: com.cleanroommc.modularui.screen.viewport.ModularGuiContext, widgetTheme: com.cleanroommc.modularui.theme.WidgetThemeEntry<*>) {
                val nowMs = Minecraft.getSystemTime()
                val dtSec = if (collapseLastFrameMs < 0L) 0.0 else ((nowMs - collapseLastFrameMs).toDouble() / 1000.0)
                collapseLastFrameMs = nowMs

                // Clamp large dt to avoid snapping after alt-tab / pauses.
                val clampedDt = kotlin.math.min(dtSec, 0.25)
                val target = if (bottomCollapsed.boolValue) 1.0f else 0.0f

                // Exponential smoothing: alpha = 1 - exp(-dt / tau)
                // Smaller tau => faster response.
                val tau = 0.10
                val alpha = (1.0 - kotlin.math.exp(-clampedDt / tau)).toFloat()

                collapseNow += (target - collapseNow) * alpha
                if (kotlin.math.abs(target - collapseNow) < 0.0005f) collapseNow = target
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
        val topTitleText = TextWidget(structureId)
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
                val t = collapseNow.coerceIn(0.0f, 1.0f)
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
        previewResetContent.child(previewResetBase)
        previewResetContent.child(previewResetBtn)

        val previewResetSlide = TransformWidget(previewResetContent)
            .pos(0, 0)
            .size(17, 19)
            .transform { stack ->
                val t = collapseNow.coerceIn(0.0f, 1.0f)
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
        val structuralFormingBtn = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("top_button/self_locking_default"),
            hover = guiTex("top_button/self_locking_selected"),
            pressed = guiTex("top_button/self_locking_pressed"),
            enabled = guiTex("top_button/self_locking_selected"),
            toggle = structuralForming
        )
            .pos(3, 15)
            .size(13, 5)

        structuralFormingContent.child(structuralFormingBase)
        structuralFormingContent.child(structuralFormingBtn)

        val structuralFormingSlide = TransformWidget(structuralFormingContent)
            .pos(0, 0)
            .size(17, 19)
            .transform { stack ->
                val t = collapseNow.coerceIn(0.0f, 1.0f)
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
                tt.addLine("Structure: $structureId")
                tt.addLine("Scan: ${if (state.enableScan.boolValue) "ON" else "OFF"}")
                tt.addLine("Mode: ${if (state.show3d.boolValue) "3D" else "Slice"}")
                tt.addLine("Slice Y: ${sliceY.intValue + model.bounds.min.y}")
                tt.addLine("Forming: ${if (structuralForming.boolValue) "ON" else "OFF"}")
            }
        structuralInfoContent.child(structuralInfo)

        val structuralInfoSlide = TransformWidget(structuralInfoContent)
            .pos(0, 0)
            .size(17, 18)
            .transform { stack ->
                val t = collapseNow.coerceIn(0.0f, 1.0f)
                stack.translate(0.0f, -15.0f * t)
            }
        structuralInfoClip.child(structuralInfoSlide)
        root.child(structuralInfoClip)

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

        // --- Left: replace preview (collapsible like top/bottom menus) ---
        // Spec note: treat this as a self-contained clipped component that slides out when component_switch is toggled.
        val leftReplaceClip = ScissorGroupWidget()
            .pos(9, 31)
            .size(18, 181)

        val leftReplaceContent = Column()
            .pos(0, 0)
            .size(18, 181)

        leftReplaceContent.child(guiTex("left_replace_preview").asWidget().pos(0, 0).size(18, 181))

        val selectedStack = materialStacks.firstOrNull() ?: net.minecraft.item.ItemStack.EMPTY
        val selectedDrawable = com.cleanroommc.modularui.drawable.ItemDrawable(selectedStack)
        leftReplaceContent.child(selectedDrawable.asWidget().pos(1, 1).size(16, 16))

        val materialsColumn = Column()
            .pos(1, 18)
            .size(16, 162)
            .childPadding(2)
            .apply { flex().coverChildrenWidth(); flex().coverChildrenHeight() }
        for (s in materialStacks.take(9)) {
            materialsColumn.child(com.cleanroommc.modularui.drawable.ItemDrawable(s).asWidget().size(16, 16))
        }
        leftReplaceContent.child(materialsColumn)

        val leftReplaceSlide = TransformWidget(leftReplaceContent)
            .pos(0, 0)
            .size(18, 181)
            .transform { stack ->
                val t = collapseNow.coerceIn(0.0f, 1.0f)
                // Slide out to the left within the clip window.
                stack.translate(-18.0f * t, 0.0f)
            }

        leftReplaceClip.child(leftReplaceSlide)
        root.child(leftReplaceClip)

        // --- Optional: material preview UI (static for now, toggled by bottom button) ---
        val showMaterialPreviewUi = BoolValue(false)
        val materialPreviewUiBg = guiTex("base_material_preview_ui").asWidget().pos(29, 31).size(134, 163)
        val materialPreviewUiContent = Column()
            .pos(33, 35)
            .size(111, 160)
            .childPadding(2)
            .apply { flex().coverChildrenWidth(); flex().coverChildrenHeight() }
        for (s in materialStacks.take(24)) {
            materialPreviewUiContent.child(com.cleanroommc.modularui.drawable.ItemDrawable(s).asWidget().size(16, 16))
        }
        root.child(materialPreviewUiBg)
        root.child(materialPreviewUiContent)

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
            .pos(1, 4)
            .size(62, 13)
        bottomSlideContent.child(baseBg)

        val led = ToggleDrawableWidget(
            valueProvider = { bottomCollapsed.boolValue },
            off = guiTex("down_button_base_off-led"),
            on = guiTex("down_button_base_on-led")
        )
            .pos(3, 7)
            .size(1, 7)
        bottomSlideContent.child(led)

        val bottomSlide = TransformWidget(bottomSlideContent)
            .pos(0, 0)
            .size(78, 16)
            .transform { stack ->
                val t = collapseNow.coerceIn(0.0f, 1.0f)
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
            .pos(6, 1)
            .size(12, 14)

        // material preview ui (toggle)
        val materialPreviewBtn = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/material_preview_ui_default"),
            hover = guiTex("down_button_selected/material_preview_ui_selected"),
            pressed = guiTex("down_button_pressed/material_preview_ui_pressed"),
            enabled = guiTex("down_button_selected/material_preview_ui_selected"),
            toggle = showMaterialPreviewUi
        )
            .pos(19, 1)
            .size(12, 14)

        // replace block cycle lock (toggle) - repurposed: toggle wireframe overlay (so block model rendering is visible)
        val replaceBlock = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/replace_block_default"),
            hover = guiTex("down_button_selected/replace_block_selected"),
            pressed = guiTex("down_button_pressed/replace_block_pressed"),
            enabled = guiTex("down_button_selected/replace_block_selected"),
            toggle = showWireframe
        )
            .pos(32, 1)
            .size(12, 14)

        // layer preview (toggle)
        val layerPreviewBtn = github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton(
            normal = guiTex("down_button_default/layer_preview_default"),
            hover = guiTex("down_button_selected/layer_preview_selected"),
            pressed = guiTex("down_button_pressed/layer_preview_pressed"),
            enabled = guiTex("down_button_selected/layer_preview_selected"),
            toggle = layerPreview
        )
            .pos(45, 1)
            .size(12, 14)

        bottomSlideContent.child(placeProjection)
        bottomSlideContent.child(materialPreviewBtn)
        bottomSlideContent.child(replaceBlock)
        bottomSlideContent.child(layerPreviewBtn)

        root.child(bottomClip)

        // Enable/disable the folded menu children based on [menuExpanded].
        root.onUpdateListener {
            // bottom collapsed => disable base buttons
            val collapsed = bottomCollapsed.boolValue
            if (placeProjection.isEnabled == collapsed) placeProjection.isEnabled = !collapsed
            if (materialPreviewBtn.isEnabled == collapsed) materialPreviewBtn.isEnabled = !collapsed
            if (replaceBlock.isEnabled == collapsed) replaceBlock.isEnabled = !collapsed
            if (layerPreviewBtn.isEnabled == collapsed) layerPreviewBtn.isEnabled = !collapsed

            // Tie preview mode to the layer preview toggle.
            state.show3d.boolValue = !layerPreview.boolValue

            // material preview UI visibility (keep it simple: enabled = visible)
            val showMat = showMaterialPreviewUi.boolValue
            if (materialPreviewUiBg.isEnabled != showMat) materialPreviewUiBg.isEnabled = showMat
            if (materialPreviewUiContent.isEnabled != showMat) materialPreviewUiContent.isEnabled = showMat

            // layer selection visibility
            val showLayer = layerPreview.boolValue
            if (layerSelectionBg.isEnabled != showLayer) layerSelectionBg.isEnabled = showLayer
            if (layerSlider.isEnabled != showLayer) layerSlider.isEnabled = showLayer

            // When not in layer preview, keep the slider panel tucked to the right (+8px) like the spec.
            val layerX = if (showLayer) 165 else 173
            layerSelectionBg.pos(layerX, 33)
            layerSlider.pos(layerX, 33)

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
