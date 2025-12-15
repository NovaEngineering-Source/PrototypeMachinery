package github.kasuminova.prototypemachinery.api.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern
import net.minecraft.util.math.BlockPos

/**
 * A [MachineStructure] that repeats a [pattern] from [minCount] to [maxCount] times.
 *
 * 可重复的切片结构：从 [minCount] 到 [maxCount] 次重复 [pattern]。
 *
 * This is an API-level contract for tooling (preview/indexing/editor) and avoids
 * requiring those systems to rely on concrete impl classes.
 */
public interface SliceLikeMachineStructure : MachineStructure {

    public val pattern: StructurePattern

    public val minCount: Int

    public val maxCount: Int

    /** Offset applied per slice iteration / 每个切片迭代应用的偏移 */
    public val sliceOffset: BlockPos

}
