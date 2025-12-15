package github.kasuminova.prototypemachinery.api.machine.structure

import github.kasuminova.prototypemachinery.api.machine.structure.pattern.StructurePattern

/**
 * A [MachineStructure] that is described by a single [StructurePattern].
 *
 * 由单个 [StructurePattern] 描述的结构。
 *
 * This interface exists primarily for tooling (preview, BOM generation, editor integration)
 * so those systems don't need to depend on concrete impl classes.
 */
public interface TemplateLikeMachineStructure : MachineStructure {

    public val pattern: StructurePattern

}
