package github.kasuminova.prototypemachinery.impl.machine.structure.preview

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.SliceLikeMachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.TemplateLikeMachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.BlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.pattern.predicate.PreviewableBlockPredicate
import github.kasuminova.prototypemachinery.api.machine.structure.preview.BlockRequirement
import github.kasuminova.prototypemachinery.api.machine.structure.preview.PreviewBomEntry
import github.kasuminova.prototypemachinery.api.machine.structure.preview.PreviewBounds
import github.kasuminova.prototypemachinery.api.machine.structure.preview.StructurePreviewModel
import github.kasuminova.prototypemachinery.api.machine.structure.preview.UnknownRequirement
import github.kasuminova.prototypemachinery.common.util.times
import net.minecraft.util.math.BlockPos

/**
 * Builds a pure [StructurePreviewModel] from a [MachineStructure].
 *
 * 从 [MachineStructure] 构建纯数据的 [StructurePreviewModel]。
 */
public object StructurePreviewBuilder {

    public data class Options(
        /**
         * Selects how many slices should be used for a [SliceLikeMachineStructure] in preview.
         *
         * 默认选 [minCount]，可由 UI/脚本覆盖。
         */
        public val sliceCountSelector: (SliceLikeMachineStructure) -> Int = { it.minCount }
    )

    public fun build(structure: MachineStructure, options: Options = Options()): StructurePreviewModel {
        val blocks = linkedMapOf<BlockPos, BlockRequirement>()

        lateinit var addStructure: (MachineStructure, BlockPos) -> Unit

        fun describe(predicate: BlockPredicate): BlockRequirement {
            return (predicate as? PreviewableBlockPredicate)?.toRequirement()
                ?: UnknownRequirement(predicate.javaClass.name)
        }

        val addTemplate: (TemplateLikeMachineStructure, BlockPos) -> Unit = { s, origin ->
            val offsetOrigin = origin.add(s.offset)
            for ((relPos, predicate) in s.pattern.blocks) {
                val actual = offsetOrigin.add(relPos)
                blocks[actual] = describe(predicate)
            }
            for (child in s.children) {
                addStructure(child, offsetOrigin)
            }
        }

        val addSlice: (SliceLikeMachineStructure, BlockPos) -> Unit = { s, origin ->
            val offsetOrigin = origin.add(s.offset)
            val selected = options.sliceCountSelector(s)
            val count = selected.coerceIn(s.minCount, s.maxCount)

            var current = offsetOrigin
            for (i in 0 until count) {
                for ((relPos, predicate) in s.pattern.blocks) {
                    val actual = current.add(relPos)
                    blocks[actual] = describe(predicate)
                }
                if (i != count - 1) {
                    current = current.add(s.sliceOffset)
                }
            }

            val accumulatedOffset = s.sliceOffset * (count - 1)
            val childOrigin = offsetOrigin.add(accumulatedOffset)
            for (child in s.children) {
                addStructure(child, childOrigin)
            }
        }

        addStructure = { s, origin ->
            when (s) {
                is TemplateLikeMachineStructure -> addTemplate(s, origin)
                is SliceLikeMachineStructure -> addSlice(s, origin)
                else -> {
                    // Unknown structure kind; still descend into children to be forgiving.
                    for (child in s.children) {
                        addStructure(child, origin.add(s.offset))
                    }
                }
            }
        }

        addStructure(structure, BlockPos.ORIGIN)

        val bounds = computeBounds(blocks.keys)
        val bom = blocks.values
            .groupingBy { it.stableKey() }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { (key, count) ->
                // Recover representative requirement instance from the blocks map.
                val req = blocks.values.first { it.stableKey() == key }
                PreviewBomEntry(req, count)
            }

        return StructurePreviewModel(
            blocks = blocks,
            bounds = bounds,
            bom = bom
        )
    }

    private fun computeBounds(positions: Collection<BlockPos>): PreviewBounds {
        if (positions.isEmpty()) {
            val zero = BlockPos(0, 0, 0)
            return PreviewBounds(zero, zero)
        }

        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        for (pos in positions) {
            if (pos.x < minX) minX = pos.x
            if (pos.y < minY) minY = pos.y
            if (pos.z < minZ) minZ = pos.z
            if (pos.x > maxX) maxX = pos.x
            if (pos.y > maxY) maxY = pos.y
            if (pos.z > maxZ) maxZ = pos.z
        }

        return PreviewBounds(BlockPos(minX, minY, minZ), BlockPos(maxX, maxY, maxZ))
    }

}
