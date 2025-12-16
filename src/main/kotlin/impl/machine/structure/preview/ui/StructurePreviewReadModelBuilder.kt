package github.kasuminova.prototypemachinery.impl.machine.structure.preview.ui

import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewBomLine
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntry
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewReadModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewStats
import net.minecraft.util.math.BlockPos

/**
 * Helpers for producing [StructurePreviewReadModel] from lower-level preview models.
 */
public object StructurePreviewReadModelBuilder {

    /**
     * Build a read model from a pure [StructurePreviewModel].
     *
     * Notes:
     * - This builder does NOT do world scanning.
     * - Hosts may later enrich entries with [StructurePreviewEntry.worldPos] and status.
     */
    public fun fromModel(
        structureId: String,
        model: StructurePreviewModel,
        orientation: StructureOrientation? = null,
        anchor: BlockPos? = null,
        entryStatusByRelativePos: Map<BlockPos, StructurePreviewEntryStatus> = emptyMap(),
        worldPosByRelativePos: Map<BlockPos, BlockPos> = emptyMap()
    ): StructurePreviewReadModel {
        val entries = model.blocks.entries.map { (relPos, req) ->
            StructurePreviewEntry(
                relativePos = relPos,
                worldPos = worldPosByRelativePos[relPos],
                requirement = req,
                status = entryStatusByRelativePos[relPos] ?: StructurePreviewEntryStatus.UNKNOWN,
                instancePath = null
            )
        }

        val stats = StructurePreviewStats.fromEntries(entries)
        val bomLines = buildBomLines(entries)

        return StructurePreviewReadModel(
            structureId = structureId,
            orientation = orientation,
            anchor = anchor,
            entries = entries,
            bom = model.bom,
            bomLines = bomLines,
            stats = stats
        )
    }

    private fun buildBomLines(entries: List<StructurePreviewEntry>): List<StructurePreviewBomLine> {
        data class Acc(
            val req: github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement,
            var required: Int = 0,
            var match: Int = 0,
            var missing: Int = 0,
            var wrong: Int = 0,
            var unloaded: Int = 0,
            var unknown: Int = 0
        )

        val byKey = linkedMapOf<String, Acc>()

        for (e in entries) {
            val key = e.requirement.stableKey()
            val acc = byKey.getOrPut(key) { Acc(e.requirement) }
            acc.required++
            when (e.status) {
                StructurePreviewEntryStatus.MATCH -> acc.match++
                StructurePreviewEntryStatus.MISSING -> acc.missing++
                StructurePreviewEntryStatus.WRONG -> acc.wrong++
                StructurePreviewEntryStatus.UNLOADED -> acc.unloaded++
                StructurePreviewEntryStatus.UNKNOWN -> acc.unknown++
            }
        }

        // Default sorting: most required first, then mismatches, then stableKey.
        return byKey.values
            .map { acc ->
                StructurePreviewBomLine(
                    requirement = acc.req,
                    requiredCount = acc.required,
                    match = acc.match,
                    missing = acc.missing,
                    wrong = acc.wrong,
                    unloaded = acc.unloaded,
                    unknown = acc.unknown
                )
            }
            .sortedWith(
                compareByDescending<StructurePreviewBomLine> { it.requiredCount }
                    .thenByDescending { it.mismatchCount }
                    .thenBy { it.key }
            )
    }
}
