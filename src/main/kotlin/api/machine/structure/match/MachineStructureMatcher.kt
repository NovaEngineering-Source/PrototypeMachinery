package github.kasuminova.prototypemachinery.api.machine.structure.match

import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData

/**
 * # MachineStructureMatcher - Structure matching strategy
 * # MachineStructureMatcher - 结构匹配策略接口
 *
 * Matches a [MachineStructure] against a world/context and produces [StructureInstanceData] when successful.
 * Returning `null` indicates the structure does not match under the given [StructureMatchContext].
 *
 * 用于在给定上下文中匹配 [MachineStructure]。
 * 匹配成功则返回 [StructureInstanceData]，返回 `null` 表示匹配失败。
 */
public interface MachineStructureMatcher<S : MachineStructure> {

    public fun match(structure: S, context: StructureMatchContext): StructureInstanceData?

}