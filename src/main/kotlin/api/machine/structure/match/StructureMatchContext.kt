package github.kasuminova.prototypemachinery.api.machine.structure.match

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstance
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData

/**
 * # StructureMatchContext - Recursive matching context
 * # StructureMatchContext - 递归匹配上下文
 *
 * Context object used during structure matching.
 * Implementations usually maintain a stack of "currently matching" structures, build [StructureInstance] trees,
 * and expose helper hooks to enter/exit nested structures.
 *
 * 结构匹配期间使用的上下文对象。
 * 实现通常维护“当前匹配结构”的栈，构建 [StructureInstance] 树，并通过 enter/exit 来管理嵌套结构。
 */
public interface StructureMatchContext {

    public val machine: MachineInstance

    public val currentMatching: MachineStructure

    public val currentMatchingData: StructureInstanceData

    /**
     * Enter a structure for matching.
     * Creates instance data and pushes context.
     *
     * 进入结构进行匹配。
     * 创建实例数据并推入上下文。
     */
    public fun enterStructure(structure: MachineStructure)

    /**
     * Exit current structure after matching.
     * Builds StructureInstance and adds to parent.
     *
     * 匹配后退出当前结构。
     * 构建 StructureInstance 并添加到父级。
     *
     * @param matched Whether the structure matched successfully / 结构是否成功匹配
     */
    public fun exitStructure(matched: Boolean)

    /**
     * Add a child instance to current structure.
     * Used when child structures are matched independently.
     *
     * 向当前结构添加子实例。
     * 当子结构独立匹配时使用。
     */
    public fun addChildInstance(structure: MachineStructure, instance: StructureInstance)

}