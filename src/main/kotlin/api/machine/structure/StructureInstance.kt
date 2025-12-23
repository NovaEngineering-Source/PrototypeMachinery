package github.kasuminova.prototypemachinery.api.machine.structure

/**
 * # StructureInstance - Matched structure instance tree
 * # StructureInstance - 已匹配的结构实例树
 *
 * Represents a matched structure instance produced by a structure matcher.
 * Instances form a tree: one root structure, with optional nested child structure instances.
 *
 * 表示结构匹配器生成的“已匹配结构实例”。
 * 实例以树状组织：根结构 + 可选的子结构实例。
 */
public interface StructureInstance {

    public val structure: MachineStructure

    public val data: StructureInstanceData

    /** Child instances grouped by their structure definition. / 子结构实例（按结构定义分组）。 */
    public val children: Map<MachineStructure, List<StructureInstance>>

}