package github.kasuminova.prototypemachinery.client.preview.ui

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
import com.cleanroommc.modularui.widgets.ButtonWidget
import com.cleanroommc.modularui.widgets.TextWidget
import com.cleanroommc.modularui.widgets.TransformWidget
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.PrototypeMachinery
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewBomLine
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import github.kasuminova.prototypemachinery.client.api.render.binding.GeckoModelBinding
import github.kasuminova.prototypemachinery.client.api.render.binding.SliceRenderMode
import github.kasuminova.prototypemachinery.client.impl.render.binding.ClientRenderBindingRegistryImpl
import github.kasuminova.prototypemachinery.client.impl.render.binding.ClientStructureRenderAnchors
import github.kasuminova.prototypemachinery.client.preview.ProjectionRenderMode
import github.kasuminova.prototypemachinery.client.preview.ProjectionVisualMode
import github.kasuminova.prototypemachinery.client.preview.StructureProjectionSession
import github.kasuminova.prototypemachinery.client.preview.WorldProjectionManager
import github.kasuminova.prototypemachinery.client.preview.scan.StructurePreviewWorldScanCache
import github.kasuminova.prototypemachinery.client.preview.ui.widget.ScissorGroupWidget
import github.kasuminova.prototypemachinery.client.preview.ui.widget.StructurePreview3DWidget
import github.kasuminova.prototypemachinery.client.util.ClientNextTick
import github.kasuminova.prototypemachinery.common.util.TwistMath
import github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder
import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.I18n
import net.minecraft.init.Blocks
import net.minecraft.util.EnumFacing
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

    private data class ControllerRenderInfo(
        val requirement: ExactBlockStateRequirement,
        val machineTypeId: ResourceLocation,
        val geckoBinding: GeckoModelBinding?
    )

    private data class GeckoPreviewContext(
        val machineTypeId: ResourceLocation,
        val orientation: StructureOrientation,
        val instances: List<StructurePreview3DWidget.GeckoPreviewInstance>
    )

    private data class ResolvedPreview(
        val model: StructurePreviewModel,
        val state: StructurePreviewUiState,
        val structureName: String,
        val structure: github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure,
    )

    private fun resolvePreview(structureId: String, sliceCountOverride: Int?, host: StructurePreviewUiHostConfig): ResolvedPreview? {
        val baseStructure = PrototypeMachineryAPI.structureRegistry.get(structureId) ?: return null

        // For embedded hosts (e.g. Build Instrument), we may have a real controller orientation.
        // In that case, build the preview model from the transformed structure so child blocks rotate too.
        val mc = Minecraft.getMinecraft()
        val locked: StructureOrientation? = host.lockedOrientationProvider?.invoke(mc)
        val structure = if (locked != null) {
            PrototypeMachineryAPI.structureRegistry.get(structureId, locked, locked.front) ?: baseStructure
        } else {
            baseStructure
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

        return ResolvedPreview(
            model = model,
            state = state,
            structureName = baseStructure.name,
            structure = structure,
        )
    }

    internal fun tr(key: String, vararg args: Any): String = I18n.format(key, *args)

    private fun onOffText(v: Boolean): String = tr(if (v) "pm.preview.ui.state.on" else "pm.preview.ui.state.off")

    private fun previewModeText(show3d: Boolean): String = tr(if (show3d) "pm.preview.ui.mode.3d" else "pm.preview.ui.mode.slice")

    internal fun entryStatusText(st: StructurePreviewEntryStatus): String = tr("pm.preview.ui.entry_status.${st.name.lowercase()}")

    /**
     * Best-effort resolve controller block requirement for a structure.
     *
     * Notes:
     * - UI currently opens by structureId only (no machineTypeId), so we infer via
     *   `MachineType.structure.id == structureId`.
     * - If multiple machine types share a structure, we pick a deterministic one (min id).
     */
    private fun resolveControllerRenderInfo(structureId: String, locked: StructureOrientation?): ControllerRenderInfo? {
        val candidates = PrototypeMachineryAPI.machineTypeRegistry.all()
            .filter { it.structure.id == structureId }
            .sortedBy { it.id.toString() }

        val machineType = candidates.firstOrNull() ?: return null
        val machineTypeId = machineType.id

        // Controller block registry name convention: <namespace>:<path>_controller
        val controllerId = ResourceLocation(machineType.id.namespace, machineType.id.path + "_controller")
        val block = Block.REGISTRY.getObject(controllerId)
        if (block === Blocks.AIR) return null

        val id = block.registryName ?: controllerId

        // NOTE: Do NOT force formed here.
        // Formed/unformed should be controlled by the UI "structural forming" toggle.
        // Here we only reflect facing+twist so the controller model rotates correctly.
        val facing = locked?.front ?: EnumFacing.NORTH
        val top = locked?.top ?: EnumFacing.UP
        val twist = runCatching { TwistMath.getTwistFromTop(facing, top) }.getOrDefault(0)
        val props = mapOf(
            "facing" to facing.name.lowercase(),
            "twist" to twist.toString(),
        )

        // Prefer structure-bound binding for this root structure; fallback to machine binding.
        val geckoBinding = ClientRenderBindingRegistryImpl.getStructureBinding(machineTypeId, structureId)?.model
            ?: ClientRenderBindingRegistryImpl.getMachineBinding(machineTypeId)

        return ControllerRenderInfo(
            requirement = ExactBlockStateRequirement(id, 0, props),
            machineTypeId = machineTypeId,
            geckoBinding = geckoBinding
        )
    }

    private fun buildGeckoPreviewContext(
        machineTypeId: ResourceLocation,
        transformedStructure: github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure,
        locked: StructureOrientation?,
        sliceCountOverride: Int?,
    ): GeckoPreviewContext {
        val orientation = locked ?: StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)

        val structureBindings = ClientRenderBindingRegistryImpl.getStructureBindings(machineTypeId)
        val machineBinding = ClientRenderBindingRegistryImpl.getMachineBinding(machineTypeId)

        // Use maxCount for Slice structures so the formed preview renders a "full" machine.
        val sliceCountsById = HashMap<String, Int>()
        fun collectSliceCounts(s: github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure) {
            val slice = s as? github.kasuminova.prototypemachinery.api.machine.structure.SliceLikeMachineStructure
            if (slice != null) {
                sliceCountsById[s.id] = sliceCountOverride ?: slice.minCount
            }
            for (c in s.children) collectSliceCounts(c)
        }
        collectSliceCounts(transformedStructure)

        val anchors = ClientStructureRenderAnchors.collectAnchorsFromSliceCounts(
            structure = transformedStructure,
            controllerPos = BlockPos(0, 0, 0),
            sliceCountsById = sliceCountsById,
            resolveSliceMode = { st ->
                structureBindings[st.id]?.sliceRenderMode ?: SliceRenderMode.STRUCTURE_ONLY
            },
        )

        val instances = ArrayList<StructurePreview3DWidget.GeckoPreviewInstance>()

        // Structure-bound models (top/mid/tail etc.)
        for (a in anchors) {
            val sb = structureBindings[a.structure.id] ?: continue
            instances.add(
                StructurePreview3DWidget.GeckoPreviewInstance(
                    binding = sb.model,
                    anchorModelPos = a.worldOrigin,
                    debugStructureId = a.structure.id,
                    sliceIndex = a.sliceIndex,
                )
            )
        }

        // Legacy machine-type model (controller anchored), used as fallback.
        if (machineBinding != null) {
            instances.add(
                StructurePreview3DWidget.GeckoPreviewInstance(
                    binding = machineBinding,
                    anchorModelPos = BlockPos(0, 0, 0),
                    debugStructureId = null,
                    sliceIndex = -1,
                )
            )
        }

        return GeckoPreviewContext(
            machineTypeId = machineTypeId,
            orientation = orientation,
            instances = instances,
        )
    }

    internal fun guiTex(path: String): UITexture {
        // ResourceLocation path is without the textures/ prefix and without .png
        // e.g. textures/gui/gui_structure_preview/base.png -> gui/gui_structure_preview/base
        return UITexture.fullImage(ResourceLocation("prototypemachinery", "gui/gui_structure_preview/$path"))
    }

    private val PANEL_BG = guiTex("base")

    // Keep slot visuals consistent with ItemHatchGUI.
    // states.png sub-area: (73,0) size 18x18.
    internal val ITEM_SLOT_BG: IDrawable = UITexture.builder()
        .location(ResourceLocation(PrototypeMachinery.MOD_ID, "textures/gui/states.png"))
        .imageSize(256, 256)
        .subAreaXYWH(73, 0, 18, 18)
        .build()

    fun open(
        structureId: String,
        sliceCountOverride: Int?,
        host: StructurePreviewUiHostConfig = StructurePreviewUiHostConfig.standalone(),
        showMaterials: Boolean = true,
        selectedPositionsProvider: (() -> Set<BlockPos>?)? = null
    ) {
        val mc = Minecraft.getMinecraft()
        val player = mc.player ?: return

        val resolved = resolvePreview(structureId, sliceCountOverride, host)
        if (resolved == null) {
            player.sendMessage(TextComponentTranslation("pm.preview.unknown_structure", structureId))
            return
        }
        val panel = buildPanel(
            state = resolved.state,
            model = resolved.model,
            structureId = structureId,
            structureName = resolved.structureName,
            structure = resolved.structure,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider,
            sliceCountOverride = sliceCountOverride,
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
        val resolved = resolvePreview(structureId, sliceCountOverride, host) ?: return null
        return buildPanel(
            state = resolved.state,
            model = resolved.model,
            structureId = structureId,
            structureName = resolved.structureName,
            structure = resolved.structure,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider,
            sliceCountOverride = sliceCountOverride,
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
        val resolved = resolvePreview(structureId, sliceCountOverride, host) ?: return null
        return buildRoot(
            state = resolved.state,
            model = resolved.model,
            structureId = structureId,
            structureName = resolved.structureName,
            structure = resolved.structure,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider,
            sliceCountOverride = sliceCountOverride,
        )
    }

    private fun buildPanel(
        state: StructurePreviewUiState,
        model: StructurePreviewModel,
        structureId: String,
        structureName: String,
        structure: github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure,
        host: StructurePreviewUiHostConfig,
        showMaterials: Boolean,
        selectedPositionsProvider: (() -> Set<BlockPos>?)?,
        sliceCountOverride: Int?,
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
            structure = structure,
            host = host,
            showMaterials = showMaterials,
            selectedPositionsProvider = selectedPositionsProvider,
            sliceCountOverride = sliceCountOverride,
        )

        panel.child(root)
        return panel
    }

    private fun buildRoot(
        state: StructurePreviewUiState,
        model: StructurePreviewModel,
        structureId: String,
        structureName: String,
        structure: github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure,
        host: StructurePreviewUiHostConfig,
        showMaterials: Boolean,
        selectedPositionsProvider: (() -> Set<BlockPos>?)?,
        sliceCountOverride: Int?,
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

        val locked = host.lockedOrientationProvider?.invoke(mc)

        // In embedded hosts, lockedOrientationProvider may become available slightly later than the initial
        // resolvePreview() call. If controller is rendered using a locked orientation but the structure model
        // was built from an unrotated structure, the controller appears rotated while the structure does not.
        // Re-resolve the transformed structure/model here using the same locked value used for controller.
        val effectiveStructure = if (locked != null) {
            PrototypeMachineryAPI.structureRegistry.get(structureId, locked, locked.front) ?: structure
        } else {
            structure
        }

        val effectiveModel: StructurePreviewModel = if (effectiveStructure !== structure) {
            StructurePreviewBuilder.build(
                structure = effectiveStructure,
                options = StructurePreviewBuilder.Options(
                    sliceCountSelector = { sliceLike ->
                        sliceCountOverride ?: sliceLike.minCount
                    }
                )
            )
        } else {
            model
        }

        val controllerInfo: ControllerRenderInfo? = resolveControllerRenderInfo(structureId, locked)

        val geckoCtx = controllerInfo?.let { info ->
            buildGeckoPreviewContext(
                machineTypeId = info.machineTypeId,
                transformedStructure = effectiveStructure,
                locked = locked,
                sliceCountOverride = sliceCountOverride,
            )
        }

        // Structural forming (toggle) - controls whether the preview is "formed".
        val structuralForming = BoolValue(false)

        // --- Bottom layer: render panel (can be covered by all other elements) ---
        // Keep slider range consistent with 3D widget: include origin (0,0,0) in bounds.
        val minY = kotlin.math.min(effectiveModel.bounds.min.y, 0)
        val maxY = kotlin.math.max(effectiveModel.bounds.max.y, 0)
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

        val hideWorldBlocks = PrototypeMachineryAPI.structureRegistry.get(structureId)?.hideWorldBlocks == true

        val view3d = StructurePreview3DWidget(
            model = effectiveModel,
            controllerRequirement = controllerInfo?.requirement,
            hideWorldBlocks = hideWorldBlocks,
            formedPreviewProvider = { structuralForming.boolValue },
            controllerOrientationProvider = { geckoCtx?.orientation ?: (locked ?: StructureOrientation(EnumFacing.NORTH, EnumFacing.UP)) },
            geckoPreviewInstances = geckoCtx?.instances.orEmpty(),
            statusProvider = { rt.statusSnapshot },
            issuesOnlyProvider = { rt.issuesOnly.boolValue },
            sliceModeProvider = { !state.show3d.boolValue },
            sliceYProvider = { rt.sliceY.intValue },
            autoRotateProvider = { rt.autoRotate.boolValue },
            wireframeProvider = { rt.showWireframe.boolValue },
            anyOfSelectionProvider = host.anyOfSelectionProvider,
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

        buildTopBar(
            root = root,
            rt = rt,
            state = state,
            model = effectiveModel,
            structureId = structureId,
            structureName = structureName,
            view3d = view3d,
            structuralForming = structuralForming
        )

        // --- Optional: materials UI ---
        val materials: MaterialsUiParts? = if (showMaterials) {
            buildMaterialsSection(
                root = root,
                host = host,
                model = effectiveModel,
                rt = rt
            )
        } else {
            null
        }

        val layerPreview = BoolValue(false)
        val rightSlider = buildRightLayerSlider(
            root = root,
            model = effectiveModel,
            sliceY = sliceY
        )

        val bottomBar = buildBottomBar(
            root = root,
            rt = rt,
            host = host,
            mc = mc,
            structureId = structureId,
            showMaterials = showMaterials,
            materials = materials,
            bottomCollapsed = bottomCollapsed,
            layerPreview = layerPreview
        )

        attachUpdateListener(
            root = root,
            rt = rt,
            host = host,
            mc = mc,
            state = state,
            model = effectiveModel,
            structureId = structureId,
            showMaterials = showMaterials,
            materials = materials,
            bottomBar = bottomBar,
            rightSlider = rightSlider
        )

        return root
    }

    private fun buildTopBar(
        root: Column,
        rt: RootRuntime,
        state: StructurePreviewUiState,
        model: StructurePreviewModel,
        structureId: String,
        structureName: String,
        view3d: StructurePreview3DWidget,
        structuralForming: BoolValue
    ) {
        // --- Top (clipped): components slide up and get masked within their own rectangles ---
        // NOTE: In the spec, each top component slides up when component_switch is pressed,
        // and the part outside the *component's own* area should be masked.

        // machine_prefix_name @ (8,11) size 115x18, slide Y -16
        val titleClip = ScissorGroupWidget()
        titleClip.pos(7, 10)
        titleClip.size(115, 18)

        val titleContent = Column()
        titleContent.pos(0, 0)
        titleContent.size(115, 18)

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
                tt.addLine(tr("pm.preview.ui.struct_info.slice_y", (rt.sliceY.intValue + model.bounds.min.y)))
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
    }

    private fun buildRightLayerSlider(
        root: Column,
        model: StructurePreviewModel,
        sliceY: IntValue
    ): RightSliderParts {
        // --- Right: layer selection slider ---
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

        return RightSliderParts(
            layerSelectionBg = layerSelectionBg,
            layerSlider = layerSlider
        )
    }

    private fun buildBottomBar(
        root: Column,
        rt: RootRuntime,
        host: StructurePreviewUiHostConfig,
        mc: Minecraft,
        structureId: String,
        showMaterials: Boolean,
        materials: MaterialsUiParts?,
        bottomCollapsed: BoolValue,
        layerPreview: BoolValue
    ): BottomBarParts {
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

                val locked = host.lockedOrientationProvider?.invoke(mc)
                WorldProjectionManager.start(
                    StructureProjectionSession(
                        structureId = structureId,
                        anchor = a,
                        sliceCountOverride = null,
                        followPlayerFacing = locked == null,
                        lockedOrientation = locked,
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

        return BottomBarParts(
            bottomCollapsed = bottomCollapsed,
            layerPreview = layerPreview,
            placeProjection = placeProjection,
            materialPreviewBtn = materialPreviewBtn,
            replaceBlock = replaceBlock,
            layerPreviewBtn = layerPreviewBtn
        )
    }

    private fun attachUpdateListener(
        root: Column,
        rt: RootRuntime,
        host: StructurePreviewUiHostConfig,
        mc: Minecraft,
        state: StructurePreviewUiState,
        model: StructurePreviewModel,
        structureId: String,
        showMaterials: Boolean,
        materials: MaterialsUiParts?,
        bottomBar: BottomBarParts,
        rightSlider: RightSliderParts
    ) {
        // Enable/disable the folded menu children based on [menuExpanded].
        root.onUpdateListener {
            // bottom collapsed => disable base buttons
            val collapsed = bottomBar.bottomCollapsed.boolValue
            if (bottomBar.placeProjection.isEnabled == collapsed) bottomBar.placeProjection.isEnabled = !collapsed
            if (showMaterials && bottomBar.materialPreviewBtn!!.isEnabled == collapsed) bottomBar.materialPreviewBtn.isEnabled = !collapsed
            if (bottomBar.replaceBlock.isEnabled == collapsed) bottomBar.replaceBlock.isEnabled = !collapsed
            if (bottomBar.layerPreviewBtn.isEnabled == collapsed) bottomBar.layerPreviewBtn.isEnabled = !collapsed

            // Tie preview mode to the layer preview toggle.
            state.show3d.boolValue = !bottomBar.layerPreview.boolValue

            if (showMaterials) {
                // material preview UI visibility:
                // - pinned open: show
                // - otherwise: keep hidden (auto-pop is handled by the left side bar now)
                val showMat = materials!!.materialPinnedOpen.boolValue
                if (materials.materialPreviewUiBg.isEnabled != showMat) materials.materialPreviewUiBg.isEnabled = showMat
                if (materials.materialPreviewUiContent.isEnabled != showMat) materials.materialPreviewUiContent.isEnabled = showMat
            }

            // layer selection visibility
            val showLayer = bottomBar.layerPreview.boolValue
            if (rightSlider.layerSelectionBg.isEnabled != showLayer) rightSlider.layerSelectionBg.isEnabled = showLayer
            if (rightSlider.layerSlider.isEnabled != showLayer) rightSlider.layerSlider.isEnabled = showLayer

            // When not in layer preview, keep the slider panel tucked to the right (+8px) like the spec.
            val layerX = if (showLayer) 165 else 173
            rightSlider.layerSelectionBg.pos(layerX, 33)
            rightSlider.layerSlider.pos(layerX, 33)

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
    }

    private fun buildMaterialsSection(
        root: Column,
        host: StructurePreviewUiHostConfig,
        model: StructurePreviewModel,
        rt: RootRuntime
    ): MaterialsUiParts {
        return StructurePreviewMaterialsSection.build(
            root = root,
            host = host,
            model = model,
            rt = rt
        )
    }

    internal fun formatRequirementShort(req: BlockRequirement): String {
        return StructurePreviewUiFormatting.formatRequirementShort(req)
    }

    private fun formatBomLine(line: StructurePreviewBomLine): String {
        return StructurePreviewUiFormatting.formatBomLine(line)
    }

    private fun colorForLine(line: StructurePreviewBomLine): Int {
        return StructurePreviewUiFormatting.colorForLine(line)
    }
}
