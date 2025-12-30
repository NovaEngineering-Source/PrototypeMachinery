package github.kasuminova.prototypemachinery.client.buildinstrument

import com.cleanroommc.modularui.value.sync.PanelSyncManager
import com.cleanroommc.modularui.widgets.layout.Column
import com.cleanroommc.modularui.widgets.layout.Flow
import github.kasuminova.prototypemachinery.api.PrototypeMachineryAPI
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.SliceLikeMachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.AnyOfRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateWithNbtRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.LiteralRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.UnknownRequirement
import github.kasuminova.prototypemachinery.client.buildinstrument.widget.BuildInstrumentProgressBarWidget
import github.kasuminova.prototypemachinery.client.buildinstrument.widget.TreeDiagramWidget
import github.kasuminova.prototypemachinery.client.preview.ui.StructurePreviewUiHostConfig
import github.kasuminova.prototypemachinery.client.preview.ui.StructurePreviewUiScreen
import github.kasuminova.prototypemachinery.client.preview.ui.widget.ScissorGroupWidget
import github.kasuminova.prototypemachinery.client.util.ItemStackDisplayUtil
import github.kasuminova.prototypemachinery.common.buildinstrument.BuildInstrumentNbt
import github.kasuminova.prototypemachinery.common.buildinstrument.BuildInstrumentUi
import github.kasuminova.prototypemachinery.common.util.times
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.network.PacketBuffer
import net.minecraft.util.EnumFacing
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.relauncher.Side
import net.minecraftforge.fml.relauncher.SideOnly
import java.awt.Color

/**
 * Client-side UI builder for Build Instrument.
 *
 * This class is loaded only on client side to avoid loading client-only widget classes on server.
 */
@SideOnly(Side.CLIENT)
internal object BuildInstrumentClientUi {

    // UI fine-tuning offsets (based on in-game alignment):
    // - Preview panel (and its buttons) need to move 4px left and 6px up.
    private const val PREVIEW_X = 4
    private const val PREVIEW_Y = 2

    // Progress bar colors (from spec)
    private val COLOR_BUILD = Color(0x17, 0xB8, 0x6D)
    private val COLOR_BUILD_TAIL = Color(0x07, 0x9B, 0x6B)
    private val COLOR_PAUSE = Color(0xEC, 0xA2, 0x3C)
    private val COLOR_PAUSE_TAIL = Color(0xD9, 0x78, 0x2F)
    private val COLOR_DIS = Color(0xD7, 0x3E, 0x42)
    private val COLOR_DIS_TAIL = Color(0xAA, 0x21, 0x2B)

    /**
     * Add client-side interactive widgets to the root container.
     *
        * Called from the client proxy (SidedProxy hook) to avoid server-side class loading.
     */
    @JvmStatic
    public fun addWidgets(root: Flow, tagProvider: () -> NBTTagCompound?, syncManager: PanelSyncManager) {
        // Per-structure-instance slice count overrides (keyed by tree node id/path).
        val sliceCountOverrides: MutableMap<String, Int> = mutableMapOf()

        // Cached per-structure-instance BOM formatting (keyed by "$path#$sliceCount").
        val bomLabelCache: MutableMap<String, List<String>> = mutableMapOf()

        // Selected structure node -> highlight positions inside the structure preview
        // (model coords: controller origin = (0,0,0)).
        var selectedStructureNode: TreeDiagramWidget.TreeNode? = null
        var highlightCacheKey: String? = null
        var highlightCache: Set<BlockPos>? = null

        val selectedPositionsProvider: () -> Set<BlockPos>? = {
            val tag = tagProvider()
            val node = selectedStructureNode
            if (node == null) {
                null
            } else {
                val overrideKey = node.structurePath ?: node.id
                val key = "${node.id}#${node.highlightRequirementKey ?: ""}#${sliceCountOverrides[overrideKey] ?: -1}"
                if (key != highlightCacheKey) {
                    highlightCacheKey = key
                    highlightCache = computeSelectedPositions(node, sliceCountOverrides)
                }
                highlightCache
            }
        }

        // --- Left: structure preview UI (reused from gui_structure_preview; materials enabled) ---
        val previewRoot = createStructurePreviewRoot(tagProvider, selectedPositionsProvider, syncManager)
        if (previewRoot != null) {
            // Left area is ~184x220 at (7,8) (based on current Build Instrument layout).
            val previewContainer = Column()
                .pos(PREVIEW_X, PREVIEW_Y)
                .size(184, 220)
            previewContainer.child(previewRoot)
            root.child(previewContainer)
        }

        // --- Tree diagram widget (clipped to avoid drawing outside the panel) ---
        val treeDiagram = TreeDiagramWidget(
            treeDataProvider = { getTreeDataForStructure(tagProvider(), sliceCountOverrides, bomLabelCache, syncManager) },
            onSelectionChanged = { node ->
                // Only structure-backed nodes update highlight selection.
                if (node == null) {
                    selectedStructureNode = null
                    highlightCacheKey = null
                    highlightCache = null
                } else if (node.structure != null && node.type != TreeDiagramWidget.NodeType.MAIN) {
                    selectedStructureNode = node
                    highlightCacheKey = null
                    highlightCache = null
                }
            }
        )
            .pos(0, 0)
            .size(185, 192)

        val treeClip = ScissorGroupWidget()
            .pos(191, 8)
            .size(185, 192)
        treeClip.child(treeDiagram)

        // --- Progress bar ---
        val progressBar = BuildInstrumentProgressBarWidget(
            stateProvider = { BuildInstrumentNbt.readTaskState(tagProvider()) },
            doneProvider = { BuildInstrumentNbt.readTaskDone(tagProvider()) },
            totalProvider = { BuildInstrumentNbt.readTaskTotal(tagProvider()) },
            buildColor = COLOR_BUILD,
            buildTailColor = COLOR_BUILD_TAIL,
            pauseColor = COLOR_PAUSE,
            pauseTailColor = COLOR_PAUSE_TAIL,
            disColor = COLOR_DIS,
            disTailColor = COLOR_DIS_TAIL
        )
            .pos(192, 207)
            .size(160, 9)

        // Add widgets to root (order matters for z-index)
        root.child(treeClip)
        root.child(progressBar)
    }

    /**
     * Get tree data for the bound structure.
     */
    private fun getTreeDataForStructure(
        tag: NBTTagCompound?,
        sliceCountOverrides: MutableMap<String, Int>,
        bomLabelCache: MutableMap<String, List<String>>,
        syncManager: PanelSyncManager
    ): TreeDiagramWidget.TreeData? {
        if (tag == null || !BuildInstrumentNbt.isBound(tag)) {
            return null
        }

        val structureId = tag.getString(BuildInstrumentNbt.KEY_BOUND_STRUCTURE_ID)
        if (structureId.isEmpty()) {
            return null
        }

        val structure = PrototypeMachineryAPI.structureRegistry.get(
            structureId,
            StructureOrientation(EnumFacing.NORTH, EnumFacing.UP),
            EnumFacing.NORTH
        ) ?: return null

        fun formatOffset(pos: net.minecraft.util.math.BlockPos): String {
            return "(${pos.x}, ${pos.y}, ${pos.z})"
        }

        fun sliceSuffix(s: MachineStructure): String {
            return if (s is SliceLikeMachineStructure) {
                "  [${s.minCount}-${s.maxCount} step ${formatOffset(s.sliceOffset)}]"
            } else {
                ""
            }
        }

        fun selectedSliceCountFor(path: String, s: SliceLikeMachineStructure): Int {
            return (sliceCountOverrides[path] ?: s.minCount).coerceIn(s.minCount, s.maxCount)
        }

        fun buildMaterialsPanelChildren(path: String, s: MachineStructure, nodeOrigin: BlockPos): Pair<Int, List<TreeDiagramWidget.TreeNode>> {
            // Build a lightweight per-node BOM. For nested slice structures inside this node,
            // we currently keep their default slice count (minCount); only this node's slice override is applied.
            val sliceCountForThis = (s as? SliceLikeMachineStructure)?.let { selectedSliceCountFor(path, it) }

            val cacheKey = "$path#${sliceCountForThis ?: 0}"
            // Build model once; we also need the structured BOM entries for AnyOfRequirement.
            val model = run {
                val options = github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder.Options(
                    sliceCountSelector = { sl ->
                        if (sliceCountForThis != null && sl === s) sliceCountForThis else sl.minCount
                    }
                )
                github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder.build(s, options)
            }

            val labels = bomLabelCache.getOrPut(cacheKey) {
                model.bom
                    .asSequence()
                    .take(24)
                    .map { entry -> "${formatRequirementShort(entry.requirement)} × ${entry.count}" }
                    .toList()
            }

            // Unique requirement types = bom.size (not cached separately; re-read with same options if needed).
            // Since this is UI-only, approximate by labels count (capped).
            val approxUnique = labels.size

            // Build interactive nodes from the structured BOM entries.
            val children = model.bom
                .asSequence()
                .take(24)
                .mapIndexed { idx, entry ->
                    val req = entry.requirement
                    val count = entry.count

                    if (req is AnyOfRequirement) {
                        val reqKey = req.stableKey()
                        val selectedKey = BuildInstrumentNbt.readMaterialSelection(tag, reqKey)
                        val selected = req.options.firstOrNull { it.stableKey() == selectedKey } ?: req.options.first()

                        val selectedIcon = stackForExact(selected)
                        val selectedName = selectedIcon?.displayName ?: formatRequirementShort(selected)

                        val optionChildren = req.options.mapIndexed { oi, opt ->
                            val optKey = opt.stableKey()
                            val chosen = optKey == selected.stableKey()
                            val icon = stackForExact(opt)
                            val name = icon?.displayName ?: formatRequirementShort(opt)
                            TreeDiagramWidget.TreeNode(
                                id = "$path/materials/$idx/opt/$oi",
                                label = name,
                                type = TreeDiagramWidget.NodeType.MATERIAL_OPTION,
                                structure = s,
                                baseOrigin = nodeOrigin,
                                structurePath = path,
                                highlightRequirementKey = reqKey,
                                iconStack = icon,
                                actionLabel = if (chosen) "已选" else "选择",
                                actionEnabled = !chosen,
                                children = emptyList(),
                                expanded = true,
                                enabled = true,
                                onActivate = if (chosen) null else {
                                    {
                                        // Persist to server (and therefore to item NBT)
                                        syncManager.callSyncedAction("pm:build_instrument_action") { p: PacketBuffer ->
                                            p.writeVarInt(BuildInstrumentUi.Action.SET_MATERIAL_SELECTION.id)
                                            p.writeString(reqKey)
                                            p.writeString(optKey)
                                        }
                                    }
                                }
                            )
                        }

                        TreeDiagramWidget.TreeNode(
                            id = "$path/materials/$idx",
                            label = "可选材料: $selectedName × $count",
                            type = TreeDiagramWidget.NodeType.MATERIAL_OPTION,
                            structure = s,
                            baseOrigin = nodeOrigin,
                            structurePath = path,
                            highlightRequirementKey = reqKey,
                            iconStack = selectedIcon,
                            children = optionChildren,
                            expanded = false,
                            enabled = true
                        )
                    } else {
                        val icon = stackForRequirement(req)
                        val name = icon?.displayName ?: formatRequirementShort(req)
                        TreeDiagramWidget.TreeNode(
                            id = "$path/materials/$idx",
                            label = "$name × $count",
                            type = TreeDiagramWidget.NodeType.MATERIAL_OPTION,
                            structure = s,
                            baseOrigin = nodeOrigin,
                            structurePath = path,
                            highlightRequirementKey = req.stableKey(),
                            iconStack = icon,
                            children = emptyList(),
                            expanded = true,
                            enabled = true
                        )
                    }
                }
                .toList()

            return approxUnique to children
        }

        fun buildAuxChildren(node: MachineStructure, path: String, nodeOrigin: BlockPos): List<TreeDiagramWidget.TreeNode> {
            val out = mutableListOf<TreeDiagramWidget.TreeNode>()

            // Slice slider node
            if (node is SliceLikeMachineStructure) {
                val current = selectedSliceCountFor(path, node)
                out += TreeDiagramWidget.TreeNode(
                    id = "$path/slice_slider",
                    label = "层数: $current/${node.maxCount}",
                    type = TreeDiagramWidget.NodeType.SLICE_SLIDER,
                    structure = null,
                    baseOrigin = nodeOrigin,
                    structurePath = path,
                    slider = TreeDiagramWidget.SliderSpec(node.minCount, node.maxCount, current),
                    onSliderChanged = { v ->
                        // Note: TreeData is rebuilt every draw; storing in map is enough.
                        // The highlight model uses this override when building positions.
                        sliceCountOverrides[path] = v
                    },
                    children = emptyList(),
                    expanded = true,
                    enabled = true
                )
            }

            // Materials panel
            val (uniqueTypes, materialChildren) = buildMaterialsPanelChildren(path, node, nodeOrigin)
            if (materialChildren.isNotEmpty()) {
                out += TreeDiagramWidget.TreeNode(
                    id = "$path/materials",
                    label = "材料" + (if (uniqueTypes > 0) "（$uniqueTypes）" else ""),
                    type = TreeDiagramWidget.NodeType.MATERIAL_PANEL,
                    structure = null,
                    baseOrigin = nodeOrigin,
                    structurePath = path,
                    children = materialChildren,
                    expanded = false,
                    enabled = true
                )
            }

            return out
        }

        // Kotlin local functions do not support forward-reference in all cases; use mutually-recursive lambdas.
        lateinit var buildChildren: (MachineStructure, String, BlockPos) -> List<TreeDiagramWidget.TreeNode>
        lateinit var buildNodeChildren: (MachineStructure, String, BlockPos) -> List<TreeDiagramWidget.TreeNode>

        fun childrenBaseOrigin(parent: MachineStructure, parentPath: String, parentOrigin: BlockPos): BlockPos {
            // Match StructurePreviewBuilder's placement semantics; but allow overriding slice count per instance.
            val offsetOrigin = parentOrigin.add(parent.offset)
            return if (parent is SliceLikeMachineStructure) {
                val count = selectedSliceCountFor(parentPath, parent)
                val acc = parent.sliceOffset * (count - 1)
                offsetOrigin.add(acc)
            } else {
                offsetOrigin
            }
        }

        buildChildren = { parent, path, parentOrigin ->
            val childOrigin = childrenBaseOrigin(parent, path, parentOrigin)
            parent.children.mapIndexed { idx, child ->
                val childPath = "$path/${child.id}#$idx"
                val label = "${child.name}${sliceSuffix(child)} @ ${formatOffset(child.offset)}"

                val aux = buildAuxChildren(child, childPath, childOrigin)
                TreeDiagramWidget.TreeNode(
                    id = childPath,
                    label = label,
                    type = TreeDiagramWidget.NodeType.SUBSTRUCTURE,
                    structure = child,
                    baseOrigin = childOrigin,
                    structurePath = childPath,
                    children = aux + buildNodeChildren(child, childPath, childOrigin)
                )
            }
        }

        // Children are always shown directly under the structure node.
        // SliceLike metadata is displayed inline via [sliceSuffix] to avoid adding an extra indentation level.
        buildNodeChildren = { node, path, nodeOrigin ->
            buildChildren(node, path, nodeOrigin)
        }

        // Create tree with structure as root node
        val rootAux = buildAuxChildren(structure, "root:$structureId", BlockPos.ORIGIN)
        val root = TreeDiagramWidget.TreeNode(
            id = "root:$structureId",
            label = structure.name + sliceSuffix(structure),
            type = TreeDiagramWidget.NodeType.MAIN,
            structure = structure,
            baseOrigin = BlockPos.ORIGIN,
            structurePath = "root:$structureId",
            children = rootAux + buildNodeChildren(structure, "root:$structureId", BlockPos.ORIGIN)
        )

        return TreeDiagramWidget.TreeData(root)
    }

    private fun computeSelectedPositions(
        node: TreeDiagramWidget.TreeNode?,
        sliceCountOverrides: Map<String, Int>
    ): Set<BlockPos>? {
        if (node == null) return null
        if (node.type == TreeDiagramWidget.NodeType.MAIN) return null
        // UI-only nodes don't map to a structure selection.
        if (node.structure == null) return null
        val structure = node.structure ?: return null
        val overrideKey = node.structurePath ?: node.id
        val reqKey = node.highlightRequirementKey

        val options = github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder.Options(
            sliceCountSelector = { sl ->
                // Only apply the override for the currently selected structure instance.
                if (structure is SliceLikeMachineStructure && sl === structure) {
                    val v = sliceCountOverrides[overrideKey] ?: structure.minCount
                    v.coerceIn(structure.minCount, structure.maxCount)
                } else {
                    sl.minCount
                }
            }
        )

        // Build a sub-model for the selected node, then shift into root model coordinates.
        val sub = github.kasuminova.prototypemachinery.impl.machine.structure.preview.StructurePreviewBuilder.build(structure, options)
        val base = node.baseOrigin
        return sub.blocks.asSequence()
            .filter { (_, r) -> reqKey == null || r.stableKey() == reqKey }
            .map { (pos, _) -> base.add(pos) }
            .toSet()
    }

    private fun stackForRequirement(req: BlockRequirement): ItemStack? {
        return when (req) {
            is ExactBlockStateRequirement -> stackForExact(req)
            is ExactBlockStateWithNbtRequirement -> stackForExact(ExactBlockStateRequirement(req.blockId, req.meta, req.properties))
            is AnyOfRequirement -> stackForExact(req.options.first())
            else -> null
        }
    }

    private fun stackForExact(req: ExactBlockStateRequirement): ItemStack? {
        val block = Block.REGISTRY.getObject(req.blockId) ?: return null
        if (block == Blocks.AIR) return null
        return ItemStackDisplayUtil.stackForBlock(block, req.meta, 1)
    }

    private fun formatRequirementShort(req: BlockRequirement): String {
        return when (req) {
            is AnyOfRequirement -> {
                // Display a compact marker; the interactive UI will show concrete choices.
                "<可选:${req.options.size}>"
            }

            is ExactBlockStateRequirement -> {
                val id = req.blockId
                if (req.properties.isEmpty()) "${id.path}:${req.meta}" else "${id.path}:${req.meta}{${req.properties.size}}"
            }

            is ExactBlockStateWithNbtRequirement -> {
                val id = req.blockId
                val props = if (req.properties.isEmpty()) "" else "{${req.properties.size}}"
                val nbt = if (req.nbtConstraints.isEmpty()) "" else "[NBT:${req.nbtConstraints.size}]"
                "${id.path}:${req.meta}$props$nbt"
            }

            is LiteralRequirement -> req.key
            is UnknownRequirement -> "<未知>"
            else -> req.stableKey()
        }
    }

    private fun createStructurePreviewRoot(
        tagProvider: () -> NBTTagCompound?,
        selectedPositionsProvider: (() -> Set<BlockPos>?)? = null,
        syncManager: PanelSyncManager
    ): com.cleanroommc.modularui.api.widget.IWidget? {
        val tag = tagProvider()
        if (tag == null || !BuildInstrumentNbt.isBound(tag)) return null

        val structureId = tag.getString(BuildInstrumentNbt.KEY_BOUND_STRUCTURE_ID)
        if (structureId.isEmpty()) return null

        // Build Instrument host:
        // - Hide close/scan controls (server-safe panel already provides controls)
        // - Enable scan by default so materialsMode=REMAINING can work
        // - Bridge AnyOf selection to item NBT via synced action
        val host = StructurePreviewUiHostConfig(
            hostName = "build_instrument",
            allowWorldScan = true,
            showScanControls = false,
            showCloseButton = false,
            defaultTo3DView = true,
            allowLocate = true,
            anchorProvider = { _ -> tagProvider()?.let { BuildInstrumentNbt.readBoundPos(it) } },
            defaultEnableScan = true,
            materialsMode = StructurePreviewUiHostConfig.MaterialsMode.REMAINING,
            anyOfSelectionProvider = { requirementKey ->
                BuildInstrumentNbt.readMaterialSelection(tagProvider(), requirementKey)
            },
            anyOfSelectionSetter = { requirementKey, selectedOptionKey ->
                syncManager.callSyncedAction("pm:build_instrument_action") { p: PacketBuffer ->
                    p.writeVarInt(BuildInstrumentUi.Action.SET_MATERIAL_SELECTION.id)
                    p.writeString(requirementKey)
                    p.writeString(selectedOptionKey)
                }
            }
        )

        return StructurePreviewUiScreen.createEmbeddedRoot(
            structureId = structureId,
            sliceCountOverride = null,
            host = host,
            showMaterials = true,
            selectedPositionsProvider = selectedPositionsProvider
        )
    }

    /**
     * Resolve controller block requirement for a structure.
     */
    private fun resolveControllerRequirement(structureId: String): ExactBlockStateRequirement? {
        val candidates = PrototypeMachineryAPI.machineTypeRegistry.all()
            .filter { it.structure.id == structureId }
            .sortedBy { it.id.toString() }

        val machineType = candidates.firstOrNull() ?: return null

        val controllerId = ResourceLocation(machineType.id.namespace, machineType.id.path + "_controller")
        val block = Block.REGISTRY.getObject(controllerId)
        if (block === Blocks.AIR) {
            return null
        }

        @Suppress("DEPRECATION")
        return ExactBlockStateRequirement(controllerId, 0)
    }
}
