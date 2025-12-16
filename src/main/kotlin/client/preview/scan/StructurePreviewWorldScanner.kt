package github.kasuminova.prototypemachinery.client.preview.scan

import github.kasuminova.prototypemachinery.api.machine.structure.StructureOrientation
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ExactBlockStateRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.ui.StructurePreviewEntryStatus
import net.minecraft.block.state.IBlockState
import net.minecraft.client.multiplayer.WorldClient
import net.minecraft.init.Blocks
import net.minecraft.util.math.BlockPos

/**
 * Incremental world scanner for a [StructurePreviewModel].
 *
 * Design goals:
 * - Budgeted per-tick scanning to avoid client hitching on huge structures.
 * - Chunk-loaded fast-fail (UNLOADED) when the position can't be evaluated.
 * - Purely client-side and optional: read-only hosts (JEI, docs) should never require it.
 */
internal class StructurePreviewWorldScanner(
    private val world: WorldClient,
    private val model: StructurePreviewModel,
    private val anchor: BlockPos
) {

    data class Key(
        val structureId: String,
        val dimension: Int,
        val anchor: BlockPos,
        val orientation: StructureOrientation?,
        val sliceCount: Int
    )

    private data class Entry(val rel: BlockPos, val req: BlockRequirement)

    private val entries: List<Entry> = model.blocks.entries.map { (rel, req) -> Entry(rel, req) }

    /** Cursor into [entries] for incremental scanning. */
    private var cursor: Int = 0

    /** Mutable status map (relative pos -> status). */
    private val statusByRel: MutableMap<BlockPos, StructurePreviewEntryStatus> = HashMap(entries.size * 2)

    /** Increments whenever at least one entry status changed. */
    var version: Long = 0
        private set

    fun snapshotStatuses(): Map<BlockPos, StructurePreviewEntryStatus> = statusByRel

    fun snapshotWorldPos(): Map<BlockPos, BlockPos> {
        // Only build when requested; avoid allocating this for JEI-like hosts.
        return entries.associate { it.rel to anchor.add(it.rel) }
    }

    /**
     * Scan up to [maxChecks] entries.
     *
     * @return true if any status changed.
     */
    fun tick(maxChecks: Int): Boolean {
        if (entries.isEmpty() || maxChecks <= 0) return false

        var changed = false
        var checks = 0
        while (checks < maxChecks) {
            if (cursor >= entries.size) {
                cursor = 0
                break
            }
            val entry = entries[cursor++]
            val worldPos = anchor.add(entry.rel)

            val next = evaluate(entry.req, worldPos)
            val prev = statusByRel[entry.rel]
            if (prev != next) {
                statusByRel[entry.rel] = next
                changed = true
            }

            checks++
        }

        if (changed) version++
        return changed
    }

    private fun evaluate(req: BlockRequirement, worldPos: BlockPos): StructurePreviewEntryStatus {
        if (!world.isBlockLoaded(worldPos)) return StructurePreviewEntryStatus.UNLOADED

        return when (req) {
            is ExactBlockStateRequirement -> {
                val state = world.getBlockState(worldPos)
                if (state.block == Blocks.AIR) {
                    // For exact-state requirements, air almost always means missing.
                    StructurePreviewEntryStatus.MISSING
                } else if (matchesExact(state, req)) {
                    StructurePreviewEntryStatus.MATCH
                } else {
                    StructurePreviewEntryStatus.WRONG
                }
            }
            else -> {
                // Literal/Unknown requirements cannot be evaluated yet.
                StructurePreviewEntryStatus.UNKNOWN
            }
        }
    }

    private fun matchesExact(state: IBlockState, req: ExactBlockStateRequirement): Boolean {
        val id = state.block.registryName ?: return false
        if (id != req.blockId) return false

        // Meta check
        val meta = state.block.getMetaFromState(state)
        if (meta != req.meta) return false

        // Properties check
        if (req.properties.isEmpty()) return true

        val props = state.properties
        for ((k, v) in req.properties) {
            val entry = props.entries.firstOrNull { (prop, _) -> prop.name == k } ?: return false
            if (entry.value.toString() != v) return false
        }

        return true
    }
}
