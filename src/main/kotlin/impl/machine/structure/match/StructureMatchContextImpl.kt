package github.kasuminova.prototypemachinery.impl.machine.structure.match

import github.kasuminova.prototypemachinery.api.machine.MachineInstance
import github.kasuminova.prototypemachinery.api.machine.structure.MachineStructure
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstance
import github.kasuminova.prototypemachinery.api.machine.structure.StructureInstanceData
import github.kasuminova.prototypemachinery.api.machine.structure.match.StructureMatchContext

public class StructureMatchContextImpl(
    override val machine: MachineInstance
) : StructureMatchContext {

    /**
     * Stack of matching contexts (for nested structures).
     * 匹配上下文栈（用于嵌套结构）。
     */
    private val contextStack: ArrayDeque<MatchingFrame> = ArrayDeque()

    /**
     * Root structure instance (built during matching).
     * 根结构实例（在匹配期间构建）。
     */
    private var rootInstance: StructureInstanceImpl? = null

    override val currentMatching: MachineStructure
        get() = contextStack.last().structure

    override val currentMatchingData: StructureInstanceData
        get() = contextStack.last().data

    /**
     * Get the completed root structure instance.
     * Only valid after successful match.
     *
     * 获取已完成的根结构实例。
     * 仅在成功匹配后有效。
     */
    public fun getRootInstance(): StructureInstance? = rootInstance

    override fun enterStructure(structure: MachineStructure) {
        val data = structure.createData()
        val frame = MatchingFrame(structure, data, mutableMapOf())
        contextStack.add(frame)
    }

    override fun exitStructure(matched: Boolean) {
        if (contextStack.isEmpty()) {
            throw IllegalStateException("No structure to exit")
        }

        val frame = contextStack.removeLast()

        if (matched) {
            // Build instance
            // 构建实例
            val instance = StructureInstanceImpl(
                structure = frame.structure,
                data = frame.data,
                children = frame.childInstances
            )

            if (contextStack.isEmpty()) {
                // This is the root
                // 这是根节点
                rootInstance = instance
            } else {
                // Add to parent
                // 添加到父级
                val parentFrame = contextStack.last()
                parentFrame.childInstances
                    .computeIfAbsent(frame.structure) { mutableListOf() }
                    .add(instance)
            }
        }
    }

    override fun addChildInstance(structure: MachineStructure, instance: StructureInstance) {
        if (contextStack.isEmpty()) {
            throw IllegalStateException("No current structure")
        }
        contextStack.last().childInstances
            .computeIfAbsent(structure) { mutableListOf() }
            .add(instance)
    }

    /**
     * Matching frame for a single structure.
     * 单个结构的匹配帧。
     */
    private data class MatchingFrame(
        val structure: MachineStructure,
        val data: StructureInstanceData,
        val childInstances: MutableMap<MachineStructure, MutableList<StructureInstance>>
    )

}

/**
 * # StructureInstanceImpl - Structure Instance Implementation
 * # StructureInstanceImpl - 结构实例实现
 */
private data class StructureInstanceImpl(
    override val structure: MachineStructure,
    override val data: StructureInstanceData,
    override val children: Map<MachineStructure, List<StructureInstance>>
) : StructureInstance