package github.kasuminova.prototypemachinery.client.preview.ui

import com.cleanroommc.modularui.value.BoolValue
import com.cleanroommc.modularui.value.IntValue
import com.cleanroommc.modularui.value.StringValue
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewBomLine
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewStats

internal class StructurePreviewUiState(
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
                val short = StructurePreviewUiScreen.formatRequirementShort(line.requirement).lowercase()
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
