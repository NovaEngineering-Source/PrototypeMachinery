package github.kasuminova.prototypemachinery.api.ecs

/**
 * # TopologicalComponentMap - Topological Sorted Component Container
 * # TopologicalComponentMap - 拓扑排序组件容器
 *
 * Generic component container that maintains components in topological order.
 * Inspired by Netty's ChannelPipeline design.
 *
 * 通用组件容器，以拓扑顺序维护组件。
 * 受 Netty 的 ChannelPipeline 设计启发。
 *
 * ## Key Features / 关键特性
 *
 * - **Topological Ordering**: Components are maintained in dependency order
 *   **拓扑排序**: 组件按依赖顺序维护
 *
 * - **Dynamic Add/Remove**: Automatically re-sorts on modifications
 *   **动态添加/删除**: 修改时自动重新排序
 *
 * - **Type-safe Lookup**: Get components by key type
 *   **类型安全查找**: 按键类型获取组件
 *
 * - **Ordered Iteration**: Iterate in topological order for processing
 *   **有序迭代**: 按拓扑顺序迭代处理
 *
 * ## Type Parameters / 类型参数
 *
 * @param K Key type for component identification / 组件标识的键类型
 * @param C Component type / 组件类型
 *
 * @see TopologicalComponentNode
 * @see github.kasuminova.prototypemachinery.impl.ecs.TopologicalComponentMapTest
 */
public interface TopologicalComponentMap<K : Any, C : Any> {

    /**
     * All components in topological order.
     * 按拓扑顺序排列的所有组件。
     */
    public val orderedComponents: List<TopologicalComponentNode<K, C>>

    /**
     * Get a component by its key.
     * 按键获取组件。
     *
     * @param key The component key / 组件键
     * @return The component, or null if not found / 组件，如果未找到则为 null
     */
    public operator fun get(key: K): C?

    /**
     * Add a dependency to an existing component.
     * Makes [dependentKey] depend on [dependencyKey].
     *
     * 向现有组件添加依赖项。
     * 使 [dependentKey] 依赖于 [dependencyKey]。
     *
     * @param dependentKey The key of the component that will depend on [dependencyKey] / 将依赖于 [dependencyKey] 的组件的键
     * @param dependencyKey The key of the component that [dependentKey] will depend on / [dependentKey] 将依赖的组件的键
     */
    public fun addDependency(dependentKey: K, dependencyKey: K)

    /**
     * Remove a dependency from an existing component.
     *
     * 从现有组件移除依赖项。
     *
     * @param dependentKey The key of the component / 组件的键
     * @param dependencyKey The key of the dependency to remove / 要移除的依赖项的键
     */
    public fun removeDependency(dependentKey: K, dependencyKey: K)

    /**
     * Add a component with dependencies.
     * 添加带有依赖的组件。
     *
     * @param key Component key / 组件键
     * @param component Component instance / 组件实例
     * @param dependencies Keys of components that must come before this one / 必须在此组件之前的组件键
     */
    public fun add(key: K, component: C, dependencies: Set<K> = emptySet())

    /**
     * Add a component that should run after the specified target.
     * 添加一个应在指定目标之后运行的组件。
     *
     * Equivalent to add(key, component, setOf(targetKey)).
     * 等同于 add(key, component, setOf(targetKey))。
     *
     * @param targetKey The key of the component that should run before this one / 应在此组件之前运行的组件键
     * @param key Component key / 组件键
     * @param component Component instance / 组件实例
     */
    public fun addAfter(targetKey: K, key: K, component: C)

    /**
     * Add a component that should run before the specified target.
     * 添加一个应在指定目标之前运行的组件。
     *
     * This modifies the dependencies of the target component to depend on the new component.
     * 这将修改目标组件的依赖关系，使其依赖于新组件。
     *
     * @param targetKey The key of the component that should run after this one / 应在此组件之后运行的组件键
     * @param key Component key / 组件键
     * @param component Component instance / 组件实例
     */
    public fun addBefore(targetKey: K, key: K, component: C)

    /**
     * Add a component at the beginning of the execution order.
     * 在执行顺序的开头添加组件。
     *
     * This makes all current root nodes (nodes with no dependencies) depend on this new node.
     * 这使所有当前的根节点（没有依赖关系的节点）都依赖于这个新节点。
     *
     * @param key Component key / 组件键
     * @param component Component instance / 组件实例
     */
    public fun addFirst(key: K, component: C)

    /**
     * Add a component at the end of the execution order.
     * 在执行顺序的末尾添加组件。
     *
     * This makes the new node depend on all current leaf nodes (nodes that no one depends on).
     * 这使新节点依赖于所有当前的叶节点（没有人依赖的节点）。
     *
     * @param key Component key / 组件键
     * @param component Component instance / 组件实例
     */
    public fun addTail(key: K, component: C)

    /**
     * Remove a component by key.
     * 按键移除组件。
     *
     * @param key The component key to remove / 要移除的组件键
     */
    public fun remove(key: K)

    /**
     * Check if a component exists.
     * 检查组件是否存在。
     *
     * @param key The component key / 组件键
     * @return true if exists / 如果存在则返回 true
     */
    public fun contains(key: K): Boolean

    /**
     * Clear all components.
     * 清除所有组件。
     */
    public fun clear()

}

/**
 * # TopologicalComponentNode - Component Node in Dependency Graph
 * # TopologicalComponentNode - 依赖图中的组件节点
 *
 * Represents a component with its dependencies in the topological graph.
 * 表示拓扑图中的组件及其依赖。
 *
 * @param K Key type / 键类型
 * @param C Component type / 组件类型
 */
public data class TopologicalComponentNode<K : Any, C : Any>(
    /** Component key / 组件键 */
    public val key: K,
    /** Component instance / 组件实例 */
    public val component: C,
    /** Keys of components this depends on / 此组件依赖的组件键 */
    public val dependencies: Set<K>
)
