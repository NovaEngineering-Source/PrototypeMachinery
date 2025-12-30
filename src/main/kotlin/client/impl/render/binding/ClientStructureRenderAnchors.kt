package github.kasuminova.prototypemachinery.client.impl.render.binding

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstance
import github.kasuminova.prototypemachinery.client.api.render.binding.SliceRenderMode
import github.kasuminova.prototypemachinery.common.util.times
import github.kasuminova.prototypemachinery.impl.machine.structure.SliceStructure
import github.kasuminova.prototypemachinery.impl.machine.structure.SliceStructureInstanceData
import github.kasuminova.prototypemachinery.impl.machine.structure.TemplateStructure
import net.minecraft.util.math.BlockPos

/**
 * Helper to convert a matched [StructureInstance] tree into a list of render anchors.
 *
 * Anchor positions are *world* positions (block coords) where a structure-bound model should be placed.
 */
internal object ClientStructureRenderAnchors {

    internal data class Anchor(
        val structure: MachineStructure,
        val worldOrigin: BlockPos,
        /** For SliceStructure anchors, the slice index; otherwise -1. */
        val sliceIndex: Int = -1,
    )

    internal fun collectAnchors(
        structure: MachineStructure,
        instance: StructureInstance,
        controllerPos: BlockPos,
        resolveSliceMode: (MachineStructure) -> SliceRenderMode,
    ): List<Anchor> {
        val out = ArrayList<Anchor>()
        collectInto(structure, instance, controllerPos, out, resolveSliceMode)
        return out
    }

    /**
     * Collect anchors without matching on the client.
     *
     * The server periodically matches the structure and syncs SliceStructure matched counts.
     * Client can reconstruct anchor positions deterministically using the structure definition.
     */
    internal fun collectAnchorsFromSliceCounts(
        structure: MachineStructure,
        controllerPos: BlockPos,
        sliceCountsById: Map<String, Int>,
        resolveSliceMode: (MachineStructure) -> SliceRenderMode,
    ): List<Anchor> {
        val out = ArrayList<Anchor>()
        collectIntoFromCounts(structure, controllerPos, out, sliceCountsById, resolveSliceMode)
        return out
    }

    private fun collectInto(
        structure: MachineStructure,
        instance: StructureInstance,
        origin: BlockPos,
        out: MutableList<Anchor>,
        resolveSliceMode: (MachineStructure) -> SliceRenderMode,
    ) {
        val offsetOrigin = origin.add(structure.offset)

        when (structure) {
            is TemplateStructure -> {
                out.add(Anchor(structure, offsetOrigin, sliceIndex = -1))

                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, offsetOrigin, out, resolveSliceMode)
                    }
                }
            }

            is SliceStructure -> {
                val matchedCount = (instance.data as? SliceStructureInstanceData)?.matchedCount ?: 0
                val count = matchedCount.coerceAtLeast(0)

                val mode = resolveSliceMode(structure)

                if (mode == SliceRenderMode.PER_SLICE) {
                    var current = offsetOrigin
                    for (i in 0 until count) {
                        out.add(Anchor(structure, current, sliceIndex = i))
                        current = current.add(structure.sliceOffset)
                    }
                } else {
                    out.add(Anchor(structure, offsetOrigin, sliceIndex = -1))
                }

                // Children attach to the last slice (mirrors server-side block-position traversal).
                val accumulatedOffset = structure.sliceOffset * (count - 1).coerceAtLeast(0)
                val childOrigin = offsetOrigin.add(accumulatedOffset)

                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, childOrigin, out, resolveSliceMode)
                    }
                }
            }

            else -> {
                // Unknown structure impl: still provide an anchor at its offset origin.
                out.add(Anchor(structure, offsetOrigin, sliceIndex = -1))

                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, offsetOrigin, out, resolveSliceMode)
                    }
                }
            }
        }
    }

    private fun collectIntoFromCounts(
        structure: MachineStructure,
        origin: BlockPos,
        out: MutableList<Anchor>,
        sliceCountsById: Map<String, Int>,
        resolveSliceMode: (MachineStructure) -> SliceRenderMode,
    ) {
        val offsetOrigin = origin.add(structure.offset)

        when (structure) {
            is TemplateStructure -> {
                out.add(Anchor(structure, offsetOrigin, sliceIndex = -1))

                for (child in structure.children) {
                    // Template children origin is simply offsetOrigin.
                    collectIntoFromCounts(child, offsetOrigin, out, sliceCountsById, resolveSliceMode)
                }
            }

            is SliceStructure -> {
                val count = (sliceCountsById[structure.id] ?: 0).coerceAtLeast(0)
                val mode = resolveSliceMode(structure)

                if (mode == SliceRenderMode.PER_SLICE) {
                    var current = offsetOrigin
                    for (i in 0 until count) {
                        out.add(Anchor(structure, current, sliceIndex = i))
                        current = current.add(structure.sliceOffset)
                    }
                } else {
                    out.add(Anchor(structure, offsetOrigin, sliceIndex = -1))
                }

                // Children attach to the last slice (mirrors server-side traversal).
                val accumulatedOffset = structure.sliceOffset * (count - 1).coerceAtLeast(0)
                val childOrigin = offsetOrigin.add(accumulatedOffset)

                for (child in structure.children) {
                    collectIntoFromCounts(child, childOrigin, out, sliceCountsById, resolveSliceMode)
                }
            }

            else -> {
                out.add(Anchor(structure, offsetOrigin, sliceIndex = -1))
                for (child in structure.children) {
                    collectIntoFromCounts(child, offsetOrigin, out, sliceCountsById, resolveSliceMode)
                }
            }
        }
    }
}
