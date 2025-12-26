package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstance
import github.kasuminova.prototypemachinery.common.util.times
import net.minecraft.util.math.BlockPos

/**
 * Collect world-space block positions covered by a matched structure instance.
 *
 * Shared by server (structure-derived components) and client (optional model hiding).
 */
internal object StructureBlockPositions {

    fun collect(
        structure: MachineStructure,
        instance: StructureInstance,
        controllerPos: BlockPos,
    ): Set<BlockPos> {
        val out = LinkedHashSet<BlockPos>()
        collectInto(structure, instance, controllerPos, out)
        return out
    }

    fun collectInto(
        structure: MachineStructure,
        instance: StructureInstance,
        controllerPos: BlockPos,
        out: MutableSet<BlockPos>,
    ) {
        val offsetOrigin = controllerPos.add(structure.offset)

        when (structure) {
            is TemplateStructure -> {
                for (relativePos in structure.pattern.blocks.keys) {
                    out.add(offsetOrigin.add(relativePos))
                }

                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, offsetOrigin, out)
                    }
                }
            }

            is SliceStructure -> {
                val matchedCount = (instance.data as? SliceStructureInstanceData)?.matchedCount ?: 0
                val count = matchedCount.coerceAtLeast(0)

                var current = offsetOrigin
                for (i in 0 until count) {
                    for (relativePos in structure.pattern.blocks.keys) {
                        out.add(current.add(relativePos))
                    }
                    current = current.add(structure.sliceOffset)
                }

                val accumulatedOffset = structure.sliceOffset * (count - 1).coerceAtLeast(0)
                val childOrigin = offsetOrigin.add(accumulatedOffset)

                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, childOrigin, out)
                    }
                }
            }

            else -> {
                // Unknown structure implementation: fallback to children traversal only.
                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, offsetOrigin, out)
                    }
                }
            }
        }
    }
}
