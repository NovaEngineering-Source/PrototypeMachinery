package github.kasuminova.prototypemachinery.client.preview.ui

import com.cleanroommc.modularui.utils.Color
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.LiteralRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.UnknownRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewBomLine
import net.minecraft.client.resources.I18n

internal object StructurePreviewUiFormatting {

    private fun tr(key: String, vararg args: Any): String = I18n.format(key, *args)

    fun formatBomLine(line: StructurePreviewBomLine): String {
        val req = formatRequirementShort(line.requirement)
        val tail = buildString {
            if (line.mismatchCount > 0) append(tr("pm.preview.ui.bom.mismatch", line.mismatchCount))
            if (line.unloaded > 0) append(tr("pm.preview.ui.bom.unloaded", line.unloaded))
            if (line.unknown > 0) append(tr("pm.preview.ui.bom.unknown", line.unknown))
        }
        return "${line.requiredCount}x $req$tail"
    }

    fun colorForLine(line: StructurePreviewBomLine): Int {
        return when {
            line.mismatchCount > 0 -> Color.RED.main
            line.unloaded > 0 -> Color.ORANGE.main
            line.unknown > 0 -> Color.GREY.brighter(1)
            else -> Color.GREEN.main
        }
    }

    fun formatRequirementShort(req: BlockRequirement): String {
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
