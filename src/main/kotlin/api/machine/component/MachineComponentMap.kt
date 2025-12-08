package github.kasuminova.prototypemachinery.api.machine.component

import github.kasuminova.prototypemachinery.api.machine.component.system.MachineSystem

/**
 * # MachineComponentMap - Component Container and Index
 * # MachineComponentMap - 组件容器和索引
 * 
 * Manages all components belonging to a machine instance. Provides efficient lookup
 * by component type and automatic system indexing for tick processing.
 * 
 * 管理属于机械实例的所有组件。提供按组件类型的高效查找和用于 tick 处理的自动系统索引。
 * 
 * ## Dual Indexing / 双重索引
 * 
 * Components are indexed in two ways:
 * 1. **By Type**: Fast O(1) lookup by MachineComponentType
 * 2. **By System**: Grouped by System for efficient tick processing
 * 
 * 组件以两种方式索引:
 * 1. **按类型**: 通过 MachineComponentType 快速 O(1) 查找
 * 2. **按系统**: 按系统分组以实现高效的 tick 处理
 * 
 * ## Cache Optimization / 缓存优化
 * 
 * The implementation includes cache invalidation for instance-based lookups.
 * Type-based lookups are always fast, but dynamic class lookups may trigger scans.
 * 
 * 实现包括基于实例查找的缓存失效。
 * 基于类型的查找总是很快，但动态类查找可能触发扫描。
 * 
 * ## Thread Safety / 线程安全
 * 
 * Not thread-safe. Should only be accessed from the main server/client thread.
 * 
 * 非线程安全。应仅从主服务器/客户端线程访问。
 * 
 * ## Related Classes / 相关类
 * 
 * - [MachineComponent] - Components stored in this map
 * - [MachineComponentType] - Type keys for component lookup
 * - [MachineSystem] - Systems that process components
 * - [github.kasuminova.prototypemachinery.api.machine.MachineInstance] - Owner of this component map
 * 
 * ## Example / 示例
 * 
 * ```kotlin
 * // Get specific component by type
 * val processor = componentMap.get(RecipeProcessorComponentType)
 * 
 * // Get all components of a class
 * val containers = componentMap.getByInstanceOf(ItemContainerComponent::class.java)
 * 
 * // Add/remove components
 * componentMap.add(myComponent)
 * componentMap.remove(myComponent)
 * ```
 * 
 * @see MachineComponent
 * @see MachineComponentType
 * @see MachineSystem
 */
public interface MachineComponentMap {

    /**
     * Map of all components indexed by their type.
     * Provides O(1) lookup by component type.
     * 
     * 按类型索引的所有组件映射。
     * 提供按组件类型的 O(1) 查找。
     */
    public val components: Map<MachineComponentType<*>, MachineComponent>

    /**
     * Map of components grouped by their processing system.
     * Used for efficient tick iteration.
     * 
     * 按处理系统分组的组件映射。
     * 用于高效的 tick 迭代。
     */
    public val systems: Map<MachineSystem<*>, MutableSet<MachineComponent>>

    /**
     * Add a component to the map.
     * Automatically indexes by both type and system.
     * 
     * 向映射添加组件。
     * 自动按类型和系统索引。
     * 
     * @param component The component to add / 要添加的组件
     */
    public fun add(component: MachineComponent)

    /**
     * Remove a component from the map.
     * Removes from both type and system indexes.
     * 
     * 从映射中移除组件。
     * 从类型和系统索引中移除。
     * 
     * @param component The component to remove / 要移除的组件
     */
    public fun remove(component: MachineComponent)

    /**
     * Get a component by its type.
     * Fast O(1) lookup.
     * 
     * 按类型获取组件。
     * 快速 O(1) 查找。
     * 
     * @param type The component type to lookup / 要查找的组件类型
     * @return The component, or null if not found / 组件，如果未找到则为 null
     */
    public fun <C : MachineComponent> get(type: MachineComponentType<C>): C?

    /**
     * Get all components that are instances of the given class.
     * Uses runtime type checking. May be slower than type-based lookup.
     * 
     * 获取给定类的所有实例组件。
     * 使用运行时类型检查。可能比基于类型的查找慢。
     * 
     * @param clazz The class to filter by / 要过滤的类
     * @return Collection of matching components / 匹配组件的集合
     */
    public fun <C : MachineComponent> getByInstanceOf(clazz: Class<out C>): Collection<C>

}