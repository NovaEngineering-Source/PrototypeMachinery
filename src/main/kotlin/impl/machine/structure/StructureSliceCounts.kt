package github.kasuminova.prototypemachinery.impl.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstance

/**
 * Collect SliceStructure matchedCount per structure id from a matched instance tree.
 *
 * This is used to avoid client-side real-time structure matching:
 * - Server performs structure matching on a schedule.
 * - Client receives only the slice counts and reconstructs render anchors deterministically.
 */
internal object StructureSliceCounts {

    fun collect(
        structure: MachineStructure,
        instance: StructureInstance,
    ): Map<String, Int> {
        val out = HashMap<String, Int>()
        collectInto(structure, instance, out)
        return out
    }

    private fun collectInto(
        structure: MachineStructure,
        instance: StructureInstance,
        out: MutableMap<String, Int>,
    ) {
        when (structure) {
            is TemplateStructure -> {
                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, out)
                    }
                }
            }

            is SliceStructure -> {
                val matchedCount = (instance.data as? SliceStructureInstanceData)?.matchedCount ?: 0
                out[structure.id] = matchedCount

                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, out)
                    }
                }
            }

            else -> {
                for (child in structure.children) {
                    val childInstances = instance.children[child].orEmpty()
                    for (childInstance in childInstances) {
                        collectInto(child, childInstance, out)
                    }
                }
            }
        }
    }
}
