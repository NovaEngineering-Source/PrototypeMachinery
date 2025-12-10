package github.kasuminova.prototypemachinery.impl.ecs

import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentMap
import github.kasuminova.prototypemachinery.api.ecs.TopologicalComponentNode

/**
 * # TopologicalComponentMapImpl - Topological Component Map Implementation
 * # TopologicalComponentMapImpl - 拓扑组件映射实现
 *
 * Implementation using Kahn's algorithm for topological sorting.
 * 使用 Kahn 算法进行拓扑排序的实现。
 *
 * ## Algorithm / 算法
 *
 * 1. Build dependency graph
 * 2. Calculate in-degrees for all nodes
 * 3. Process nodes with zero in-degree
 * 4. Remove processed nodes and update in-degrees
 * 5. Repeat until all nodes are processed
 *
 * Time Complexity: O(V + E) where V = nodes, E = edges
 * 时间复杂度: O(V + E)，其中 V = 节点数，E = 边数
 *
 * @param K Key type / 键类型
 * @param C Component type / 组件类型
 */
public open class TopologicalComponentMapImpl<K : Any, C : Any> : TopologicalComponentMap<K, C> {

    private val nodes: MutableMap<K, TopologicalComponentNode<K, C>> = LinkedHashMap()
    
    // Adjacency list: Key -> Set of keys that depend on it (Dependency -> Dependents)
    // 邻接表：键 -> 依赖于它的键集合 (依赖项 -> 依赖者)
    private val dependents: MutableMap<K, MutableSet<K>> = mutableMapOf()

    // Track dependencies that are missing from the map: Missing Key -> Set of keys that depend on it
    // 跟踪映射中缺失的依赖项：缺失的键 -> 依赖于它的键集合
    private val pendingDependents: MutableMap<K, MutableSet<K>> = mutableMapOf()

    /**
     * Stores dependencies that should be applied to a component when it is added.
     * Key: The component that is not yet added (target).
     * Value: Set of components that the target should depend on.
     * 
     * 存储当组件被添加时应应用的依赖项。
     * 键: 尚未添加的组件 (目标)。
     * 值: 目标应依赖的组件集合。
     */
    private val pendingReverseDependencies: MutableMap<K, MutableSet<K>> = mutableMapOf()
    
    private var cachedOrder: List<TopologicalComponentNode<K, C>> = emptyList()
    private var dirty: Boolean = false

    override val orderedComponents: List<TopologicalComponentNode<K, C>>
        get() {
            if (dirty) {
                cachedOrder = computeTopologicalOrder()
                dirty = false
            }
            return cachedOrder
        }

    override operator fun get(key: K): C? = nodes[key]?.component

    override fun addDependency(dependentKey: K, dependencyKey: K) {
        val dependentNode = nodes[dependentKey]
        if (dependentNode != null) {
            // Node exists, update it
            if (dependencyKey !in dependentNode.dependencies) {
                val newDependencies = dependentNode.dependencies + dependencyKey
                nodes[dependentKey] = dependentNode.copy(dependencies = newDependencies)
                
                // Update graph
                if (nodes.containsKey(dependencyKey)) {
                    dependents[dependencyKey]!!.add(dependentKey)
                } else {
                    pendingDependents.computeIfAbsent(dependencyKey) { mutableSetOf() }.add(dependentKey)
                }
                dirty = true
            }
        } else {
            // Node doesn't exist yet, record pending reverse dependency
            // When dependentKey is added, it should depend on dependencyKey
            pendingReverseDependencies.computeIfAbsent(dependentKey) { mutableSetOf() }.add(dependencyKey)
        }
    }

    override fun removeDependency(dependentKey: K, dependencyKey: K) {
        val dependentNode = nodes[dependentKey]
        if (dependentNode != null) {
            if (dependencyKey in dependentNode.dependencies) {
                val newDependencies = dependentNode.dependencies - dependencyKey
                nodes[dependentKey] = dependentNode.copy(dependencies = newDependencies)
                
                // Update graph
                if (nodes.containsKey(dependencyKey)) {
                    dependents[dependencyKey]?.remove(dependentKey)
                } else {
                    pendingDependents[dependencyKey]?.remove(dependentKey)
                }
                dirty = true
            }
        } else {
            // Remove from pending if exists
            pendingReverseDependencies[dependentKey]?.remove(dependencyKey)
        }
    }

    override fun add(key: K, component: C, dependencies: Set<K>) {
        if (nodes.containsKey(key)) {
            remove(key)
        }

        // Check for pending reverse dependencies (dependencies that others requested this component to have)
        // 检查待定的反向依赖项（其他组件请求此组件拥有的依赖项）
        val pendingDeps = pendingReverseDependencies.remove(key) ?: emptySet()
        val finalDependencies = if (pendingDeps.isNotEmpty()) dependencies + pendingDeps else dependencies

        // Soft dependency check: we allow dependencies that don't exist yet.
        // They will be ignored during topological sort until they are added.
        // 软依赖检查：我们允许尚不存在的依赖项。
        // 在添加之前，它们将在拓扑排序期间被忽略。
        nodes[key] = TopologicalComponentNode(key, component, finalDependencies)
        
        // Update graph structures
        // 更新图结构
        dependents[key] = mutableSetOf()
        
        // 1. Handle dependencies of the new node
        for (dep in finalDependencies) {
            if (nodes.containsKey(dep)) {
                dependents[dep]!!.add(key)
            } else {
                pendingDependents.computeIfAbsent(dep) { mutableSetOf() }.add(key)
            }
        }
        
        // 2. Handle nodes that were waiting for this new node (as a dependency)
        val waiting = pendingDependents.remove(key)
        if (waiting != null) {
            dependents[key]!!.addAll(waiting)
        }
        
        dirty = true
    }

    override fun addAfter(targetKey: K, key: K, component: C) {
        add(key, component, setOf(targetKey))
    }

    override fun addBefore(targetKey: K, key: K, component: C) {
        // Add the new component
        add(key, component, emptySet())

        // Modify target component to depend on the new component
        val targetNode = nodes[targetKey]
        if (targetNode != null) {
            val newDependencies = targetNode.dependencies + key
            nodes[targetKey] = targetNode.copy(dependencies = newDependencies)
            
            // Update graph: target depends on key
            dependents[key]!!.add(targetKey)
            
            dirty = true
        } else {
            // Target doesn't exist yet. Record this dependency for later.
            // 目标尚不存在。记录此依赖项以备后用。
            pendingReverseDependencies.computeIfAbsent(targetKey) { mutableSetOf() }.add(key)
        }
    }

    override fun addFirst(key: K, component: C) {
        // Find all current root nodes (nodes with no active dependencies)
        // 查找所有当前根节点（没有活动依赖项的节点）
        // Optimized: Check dependencies against existing nodes
        val roots = nodes.values.filter { node ->
            node.dependencies.none { nodes.containsKey(it) }
        }.map { it.key }

        // Add new component
        add(key, component, emptySet())

        // Make all roots depend on new component
        for (rootKey in roots) {
            if (rootKey == key) continue // Don't depend on self
            val rootNode = nodes[rootKey]!!
            nodes[rootKey] = rootNode.copy(dependencies = rootNode.dependencies + key)
            
            // Update graph: root depends on key
            dependents[key]!!.add(rootKey)
        }
        dirty = true
    }

    override fun addTail(key: K, component: C) {
        // Depend on all current sink nodes (nodes that no one depends on)
        // 依赖于所有当前汇点节点（没有人依赖的节点）
        // Optimized: Use dependents map to find sinks
        val sinks = dependents.filter { it.value.isEmpty() }.keys
        
        add(key, component, sinks)
    }

    override fun remove(key: K) {
        val node = nodes.remove(key) ?: return
        
        // 1. Remove from dependents of its dependencies
        for (dep in node.dependencies) {
            if (nodes.containsKey(dep)) {
                dependents[dep]?.remove(key)
            } else {
                pendingDependents[dep]?.remove(key)
            }
        }
        
        // 2. Handle its dependents
        val myDependents = dependents.remove(key)
        if (myDependents != null) {
            for (dep in myDependents) {
                // dep depended on key, now key is gone.
                // Add to pendingDependents so if key comes back, link is restored.
                pendingDependents.computeIfAbsent(key) { mutableSetOf() }.add(dep)
            }
        }
        
        dirty = true
    }

    override fun contains(key: K): Boolean = nodes.containsKey(key)

    override fun clear() {
        nodes.clear()
        dependents.clear()
        pendingDependents.clear()
        pendingReverseDependencies.clear()
        cachedOrder = emptyList()
        dirty = false
    }

    /**
     * Compute topological order using Kahn's algorithm.
     * 使用 Kahn 算法计算拓扑顺序。
     */
    private fun computeTopologicalOrder(): List<TopologicalComponentNode<K, C>> {
        if (nodes.isEmpty()) {
            return emptyList()
        }

        // Use maintained dependents map as adjacency list
        // 使用维护的 dependents 映射作为邻接表
        // We need a copy of in-degrees because we modify them during sort
        val inDegree = mutableMapOf<K, Int>()

        // Initialize in-degrees
        for (key in nodes.keys) {
            inDegree[key] = 0
        }

        // Calculate in-degrees based on active graph
        for ((_, deps) in dependents) {
            for (dep in deps) {
                inDegree[dep] = (inDegree[dep] ?: 0) + 1
            }
        }

        // Kahn's algorithm
        val queue = ArrayDeque<K>()
        val result = mutableListOf<TopologicalComponentNode<K, C>>()

        // Add all nodes with zero in-degree
        for ((key, degree) in inDegree) {
            if (degree == 0) {
                queue.add(key)
            }
        }

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(nodes.getValue(current))

            // Reduce in-degree for neighbors
            val neighbors = dependents[current]
            if (neighbors != null) {
                for (neighbor in neighbors) {
                    val newDegree = inDegree.getValue(neighbor) - 1
                    inDegree[neighbor] = newDegree
                    if (newDegree == 0) {
                        queue.add(neighbor)
                    }
                }
            }
        }

        // Check for cycles
        if (result.size != nodes.size) {
            val remaining = nodes.keys - result.map { it.key }.toSet()
            throw IllegalStateException("Circular dependency detected among: $remaining")
        }

        return result
    }

}
