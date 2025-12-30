package github.kasuminova.prototypemachinery.client.preview.ui

import com.cleanroommc.modularui.value.BoolValue
import com.cleanroommc.modularui.value.IntValue
import com.cleanroommc.modularui.widget.Widget
import com.cleanroommc.modularui.widgets.layout.Column
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import github.kasuminova.prototypemachinery.client.preview.ui.widget.TriStateTextureButton
import net.minecraft.util.math.BlockPos

/**
 * A Column that we frequently rebuild (remove + re-add children) at runtime.
 *
 * ModularUI 的布局系统会在 children 变化时自行重新计算，所以这里只需要确保 removeAll() 被调用即可。
 */
internal class ClearableColumn : Column() {
    fun clearChildrenSafe() {
        removeAll()
        scheduleResize()
    }
}

internal class RootRuntime(
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

internal data class MaterialsUiParts(
    val materialPinnedOpen: BoolValue,
    val materialPreviewUiBg: Widget<*>,
    val materialPreviewUiContent: ClearableColumn
)

internal data class RightSliderParts(
    val layerSelectionBg: Widget<*>,
    val layerSlider: Widget<*>
)

internal data class BottomBarParts(
    val bottomCollapsed: BoolValue,
    val layerPreview: BoolValue,
    val placeProjection: TriStateTextureButton,
    val materialPreviewBtn: TriStateTextureButton?,
    val replaceBlock: TriStateTextureButton,
    val layerPreviewBtn: TriStateTextureButton
)
